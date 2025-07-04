package uk.gov.hmcts.reform.hmc.service;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.config.MessageSenderConfiguration;
import uk.gov.hmcts.reform.hmc.config.MessageType;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.config.SyncMessage;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_PROCESSING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_HMI_OUTBOUND_ADAPTER;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.MESSAGE_ERROR;
import static uk.gov.hmcts.reform.hmc.constants.Constants.NOT_DEFINED;
import static uk.gov.hmcts.reform.hmc.constants.Constants.READ;
import static uk.gov.hmcts.reform.hmc.constants.Constants.WITH_ERROR;

@Slf4j
@Service
public class MessageProcessor {

    private final ServiceBusMessageErrorHandler errorHandler;
    private final DefaultFutureHearingRepository futureHearingRepository;
    private final MessageSenderConfiguration messageSenderConfiguration;
    private final ObjectMapper objectMapper;
    private final PendingRequestService pendingRequestService;
    private static final String HEARING_ID = "hearing_id";
    public static final String MESSAGE_TYPE = "message_type";
    public static final String MISSING_CASE_LISTING_ID = "Message is missing custom header hearing_id";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "Message has unsupported value for message_type";
    public static final String MESSAGE_SUCCESS = "Message with id '{}' handled successfully";
    public static final String MISSING_MESSAGE_TYPE = "Message is missing custom header message_type";
    private static final String LA_SYNC_HEARING_RESPONSE = "LA_SYNC_HEARING_RESPONSE";

    public MessageProcessor(DefaultFutureHearingRepository futureHearingRepository,
                            ServiceBusMessageErrorHandler errorHandler,
                            MessageSenderConfiguration messageSenderConfiguration,
                            ObjectMapper objectMapper,
                            PendingRequestService pendingRequestService) {
        this.errorHandler = errorHandler;
        this.futureHearingRepository = futureHearingRepository;
        this.messageSenderConfiguration = messageSenderConfiguration;
        this.objectMapper = objectMapper;
        this.pendingRequestService = pendingRequestService;
    }

    @Value("${pending.request.pending-wait-in-milliseconds:120000}")
    private Long pendingWaitInMilliseconds;

    @Scheduled(fixedRateString = "${pending.request.pending-wait-in-milliseconds:120000}") // Execute every 2 minutes
    @Transactional
    public void processPendingRequests() {
        log.debug("processPendingRequests (every {})- starting", pendingWaitInMilliseconds);

        pendingRequestService.deleteCompletedPendingRequests();

        pendingRequestService.escalatePendingRequests();

        List<PendingRequestEntity> pendingRequests = pendingRequestService.findQueuedPendingRequestsForProcessing();
        if (pendingRequests.isEmpty()) {
            log.debug("No pending requests found for processing.");
        } else {
            log.debug("process batch of {} PendingRequests", pendingRequests.size());
            pendingRequests.forEach(this::processPendingRequest);
        }
        log.debug("processPendingRequests - completed");
    }

    @Transactional
    public void processPendingRequest(PendingRequestEntity pendingRequest) {
        log.debug("processPendingRequest(pendingRequest) starting : {}", pendingRequest.getHearingId());

        if (pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)
            || !pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)) {
            log.debug("Pending request with Id: {}, hearingId: {} is not ready for processing.",
                      pendingRequest.getId(), pendingRequest.getHearingId());
            return;
        }

        pendingRequestService.findAndLockByHearingId(pendingRequest.getHearingId());

        int claimed = pendingRequestService.claimRequest(pendingRequest.getId());
        if (claimed == 0) {
            log.debug("Pending request with Id: {}, hearingId: {} already claimed.", pendingRequest.getId(),
                      pendingRequest.getHearingId());
            return;
        }

        try {
            processPendingMessage(
                convertMessage(pendingRequest.getMessage()),
                pendingRequest.getHearingId().toString(), pendingRequest.getMessageType()
            );
        } catch (AuthenticationException | BadFutureHearingRequestException
                 | ResourceNotFoundException exception) {
            log.debug("{} {}", exception.getClass().getSimpleName(), exception.getMessage());
            pendingRequestService.markRequestWithGivenStatus(
                pendingRequest.getId(),
                PendingStatusType.EXCEPTION.name()
            );
            pendingRequestService.catchExceptionAndUpdateHearing(pendingRequest.getHearingId(), exception);
            return;
        } catch (Exception ex) {
            log.debug("Exception {}", ex.getMessage());
            pendingRequestService.markRequestAsPending(
                pendingRequest.getId(),
                pendingRequest.getRetryCount(),
                pendingRequest.getLastTriedDateTime()
            );
            return;
        }
        pendingRequestService.markRequestWithGivenStatus(
            pendingRequest.getId(),
            PendingStatusType.COMPLETED.name()
        );
        log.debug("processPendingRequest(pendingRequest) completed");
    }

    public void processMessage(ServiceBusReceivedMessageContext messageContext) {
        var message = messageContext.getMessage();
        var processingResult = tryProcessMessage(message);
        finaliseMessage(messageContext, processingResult);
        messageContext.complete();
    }

    public void processMessage(JsonNode message, Map<String, Object> applicationProperties)
            throws JsonProcessingException {
        if (log.isDebugEnabled()) {
            log.debug("processMessage message, applicationProperties");
            log.debug("message <{}>", message);
            log.debug("applicationProperties <{}>", applicationProperties);
        }

        if (applicationProperties.containsKey(MESSAGE_TYPE)) {
            MessageType messageType;
            try {
                messageType =
                    MessageType.valueOf(applicationProperties.get(MESSAGE_TYPE).toString());
            } catch (Exception exception) {
                throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }

            String caseListingID;
            try {
                caseListingID = applicationProperties.get(HEARING_ID).toString();
            } catch (Exception exception) {
                throw new MalformedMessageException(MISSING_CASE_LISTING_ID);
            }

            switch (messageType) {
                case REQUEST_HEARING:
                    log.debug("Message of type REQUEST_HEARING received for caseListingID: {} ,{}",
                              caseListingID, message);
                    processSyncFutureHearingResponse(() -> futureHearingRepository
                        .createHearingRequest(message, caseListingID), caseListingID);
                    break;
                case AMEND_HEARING:
                    log.debug("Message of type AMEND_HEARING received for caseListingID: {} ,{}",
                              caseListingID, message);
                    processSyncFutureHearingResponse(() -> futureHearingRepository
                        .amendHearingRequest(message, caseListingID), caseListingID);
                    break;
                case DELETE_HEARING:
                    log.debug("Message of type DELETE_HEARING received for caseListingID: {} ,{}",
                              caseListingID, message);
                    processSyncFutureHearingResponse(() -> futureHearingRepository
                        .deleteHearingRequest(message, caseListingID), caseListingID);
                    break;
                default:
                    throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }

        } else {
            throw new MalformedMessageException(MISSING_MESSAGE_TYPE);
        }
    }

    private void processPendingMessage(JsonNode message, String hearingId, String messageTypeString)
        throws IOException {
        log.debug("processPendingMessage");
        log.debug("hearingId<{}> messageType<{}> message<{}>", hearingId, messageTypeString, message);

        MessageType messageType;
        try {
            messageType = MessageType.valueOf(messageTypeString);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
        }

        String caseListingID = hearingId;

        switch (messageType) {
            case REQUEST_HEARING:
                log.debug("Message of type REQUEST_HEARING received for caseListingID: {} ,{}",
                          caseListingID, message);
                processSyncFutureHearingResponse(() -> futureHearingRepository
                    .createHearingRequest(message, caseListingID), caseListingID);
                break;
            case AMEND_HEARING:
                log.debug("Message of type AMEND_HEARING received for caseListingID: {} ,{}",
                          caseListingID, message);
                processSyncFutureHearingResponse(() -> futureHearingRepository
                    .amendHearingRequest(message, caseListingID), caseListingID);
                break;
            case DELETE_HEARING:
                log.debug("Message of type DELETE_HEARING received for caseListingID: {} ,{}",
                          caseListingID, message);
                processSyncFutureHearingResponse(() -> futureHearingRepository
                    .deleteHearingRequest(message, caseListingID), caseListingID);
                break;
            default:
                throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
        }
    }

    public void processException(ServiceBusErrorContext context) {
        log.error("Processed message queue handle error {}", context.getErrorSource(), context.getException());
        log.error(
            ERROR_PROCESSING_MESSAGE,
            HMC_HMI_OUTBOUND_ADAPTER,
            HMC_TO_HMI,
            READ,
            NOT_DEFINED
        );
    }

    private void finaliseMessage(ServiceBusReceivedMessageContext messageContext,
                                 MessageProcessingResult processingResult) {
        var message = messageContext.getMessage();
        switch (processingResult.resultType) {
            case SUCCESS:
                log.debug(MESSAGE_SUCCESS, messageContext.getMessage().getMessageId());
                break;
            case APPLICATION_ERROR:
                errorHandler.handleApplicationError(messageContext, processingResult.exception);
                break;
            case GENERIC_ERROR:
                errorHandler.handleGenericError(messageContext, processingResult.exception);
                break;
            case JSON_ERROR:
                errorHandler.handleJsonError(messageContext, (JsonProcessingException) processingResult.exception);
                break;
            default:
                log.info("Letting 'processed envelope' message with ID {} return to the queue. Delivery attempt {}.",
                        message.getMessageId(),
                        message.getDeliveryCount() + 1
                );
                break;
        }
    }

    private MessageProcessingResult tryProcessMessage(ServiceBusReceivedMessage message) {
        try {
            log.debug(
                "Started processing ServiceBusReceivedMessage with ID {} (delivery {})",
                message.getMessageId(),
                message.getDeliveryCount() + 1
            );

            processMessage(
                convertMessage(message.getBody()),
                message.getApplicationProperties()
            );

            log.debug("Processed message with ID {} processed successfully", message.getMessageId());
            return new MessageProcessingResult(MessageProcessingResultType.SUCCESS);

        } catch (MalformedMessageException ex) {
            logErrors(message, ex);
            return new MessageProcessingResult(MessageProcessingResultType.GENERIC_ERROR, ex);
        } catch (BadFutureHearingRequestException | AuthenticationException | ResourceNotFoundException ex) {
            logErrors(message, ex);
            return new MessageProcessingResult(MessageProcessingResultType.APPLICATION_ERROR, ex);
        } catch (JsonProcessingException ex) {
            logErrors(message, ex);
            return new MessageProcessingResult(MessageProcessingResultType.JSON_ERROR, ex);
        } catch (Exception ex) {
            logErrors(message, ex);
            return new MessageProcessingResult(MessageProcessingResultType.GENERIC_ERROR, ex);
        }
    }

    private void logErrors(Object message, Exception exception) {
        log.error("Unexpected Error", exception);
        Map<String, Object> applicationProperties;

        switch (message) {
            case ServiceBusReceivedMessage serviceBusReceivedMessage ->
                applicationProperties = serviceBusReceivedMessage.getApplicationProperties();
            case ServiceBusMessage serviceBusMessage ->
                applicationProperties = serviceBusMessage.getApplicationProperties();
            default ->
                throw new IllegalArgumentException("Unsupported message type");
        }

        log.error(
            ERROR_PROCESSING_MESSAGE,
            HMC_HMI_OUTBOUND_ADAPTER,
            HMC_TO_HMI,
            READ,
            applicationProperties.getOrDefault(HEARING_ID, NOT_DEFINED)
        );
    }

    private void processSyncFutureHearingResponse(Supplier<HearingManagementInterfaceResponse> responseSupplier,
                                                  String hearingId)
        throws JsonProcessingException, BadFutureHearingRequestException {
        SyncMessage syncMessage;
        try {
            responseSupplier.get();
            syncMessage = SyncMessage.builder()
                .listAssistHttpStatus(202)
                .build();
        } catch (BadFutureHearingRequestException ex) {
            final Integer errorCode = (null == ex.getErrorDetails() ? null : ex.getErrorDetails().getErrorCode());
            final String errorDescription =
                (null == ex.getErrorDetails() ? null : ex.getErrorDetails().getErrorDescription());
            log.error(MESSAGE_ERROR + errorCode
                          + WITH_ERROR + ex.getMessage()
                          + HEARING_ID + hearingId);
            syncMessage = SyncMessage.builder()
                .listAssistHttpStatus(400)
                .listAssistErrorCode(errorCode)
                .listAssistErrorDescription(errorDescription)
                .build();
            throw ex;
        }
        log.debug("preparing to send message to queue for hearingId {} ", hearingId);
        messageSenderConfiguration.sendMessage(objectMapper
            .writeValueAsString(syncMessage), LA_SYNC_HEARING_RESPONSE, hearingId);
    }

    private JsonNode convertMessage(BinaryData message) throws JsonProcessingException {
        return objectMapper.readTree(message.toString());
    }

    public JsonNode convertMessage(String message) throws JsonProcessingException {
        return objectMapper.readTree(message);
    }

    static class MessageProcessingResult {
        public final MessageProcessingResultType resultType;
        public final Exception exception;

        public MessageProcessingResult(MessageProcessingResultType resultType) {
            this(resultType, null);
        }

        public MessageProcessingResult(MessageProcessingResultType resultType, Exception exception) {
            this.resultType = resultType;
            this.exception = exception;
        }
    }

    enum MessageProcessingResultType {
        SUCCESS,
        GENERIC_ERROR,
        APPLICATION_ERROR,
        JSON_ERROR
    }

}

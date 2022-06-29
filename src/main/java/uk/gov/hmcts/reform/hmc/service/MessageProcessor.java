package uk.gov.hmcts.reform.hmc.service;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.config.MessageSenderConfiguration;
import uk.gov.hmcts.reform.hmc.config.MessageType;
import uk.gov.hmcts.reform.hmc.config.SyncMessage;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
public class MessageProcessor {

    private final ServiceBusMessageErrorHandler errorHandler;
    private final DefaultFutureHearingRepository futureHearingRepository;
    private final MessageSenderConfiguration messageSenderConfiguration;
    private final ObjectMapper objectMapper;
    private static final String HEARING_ID = "hearing_id";
    private static final String MESSAGE_TYPE = "message_type";
    public static final String MISSING_CASE_LISTING_ID = "Message is missing custom header hearing_id";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "Message has unsupported value for message_type";
    public static final String MISSING_MESSAGE_TYPE = "Message is missing custom header message_type";
    private static final String LA_SYNC_HEARING_RESPONSE = "LA_SYNC_HEARING_RESPONSE";

    public MessageProcessor(DefaultFutureHearingRepository futureHearingRepository,
                            ServiceBusMessageErrorHandler errorHandler,
                            MessageSenderConfiguration messageSenderConfiguration,
                            ObjectMapper objectMapper) {
        this.errorHandler = errorHandler;
        this.futureHearingRepository = futureHearingRepository;
        this.messageSenderConfiguration = messageSenderConfiguration;
        this.objectMapper = objectMapper;
    }

    public void processMessage(ServiceBusReceiverClient client, ServiceBusReceivedMessage message) {
        try {
            log.info("Received message with id '{}'", message.getMessageId());
            processMessage(
                convertMessage(message.getBody()),
                message.getApplicationProperties()
            );
            client.complete(message);
            log.info("Message with id '{}' handled successfully", message.getMessageId());

        } catch (MalformedMessageException ex) {
            errorHandler.handleGenericError(client, message, ex);
        } catch (BadFutureHearingRequestException | AuthenticationException | ResourceNotFoundException ex) {
            errorHandler.handleApplicationError(client, message, ex);
        } catch (JsonProcessingException ex) {
            errorHandler.handleJsonError(client, message, ex);
        } catch (Exception ex) {
            log.warn("Unexpected Error");
            errorHandler.handleGenericError(client, message, ex);
        }
    }

    public void processMessage(JsonNode message, Map<String, Object> applicationProperties)
        throws JsonProcessingException {
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
                    log.debug("Message of type REQUEST_HEARING received");
                    processSyncFutureHearingResponse(() -> futureHearingRepository
                        .createHearingRequest(message), caseListingID);
                    break;
                case AMEND_HEARING:
                    log.debug("Message of type AMEND_HEARING received");
                    processSyncFutureHearingResponse(() -> futureHearingRepository
                        .amendHearingRequest(message, caseListingID), caseListingID);
                    break;
                case DELETE_HEARING:
                    log.debug("Message of type DELETE_HEARING received");
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

    private void processSyncFutureHearingResponse(Supplier<HearingManagementInterfaceResponse> responseSupplier,
                                                  String hearingId)
        throws JsonProcessingException {
        SyncMessage syncMessage;
        try {
            responseSupplier.get();
            syncMessage = SyncMessage.builder()
                .listAssistHttpStatus(202)
                .build();
        } catch (BadFutureHearingRequestException ex) {
            ErrorDetails errorDetails = ex.getErrorDetails();
            syncMessage = SyncMessage.builder()
                .listAssistHttpStatus(400)
                .listAssistErrorCode(errorDetails.getErrorCode())
                .listAssistErrorDescription(errorDetails.getErrorDescription())
                .build();
        }

        messageSenderConfiguration.sendMessage(objectMapper
            .writeValueAsString(syncMessage), LA_SYNC_HEARING_RESPONSE, hearingId);
    }

    private JsonNode convertMessage(BinaryData message) throws JsonProcessingException {
        return objectMapper.readTree(message.toString());
    }
}

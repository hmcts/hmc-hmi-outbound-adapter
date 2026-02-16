package uk.gov.hmcts.reform.hmc.errorhandling;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditService;

import java.util.Map;
import java.util.Optional;

import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_PROCESSING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.FAILURE_STATUS;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HEARING_ID;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_HMI_OUTBOUND_ADAPTER;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_AUTH;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.NOT_DEFINED;
import static uk.gov.hmcts.reform.hmc.constants.Constants.READ;

@Slf4j
@Component
public class ServiceBusMessageErrorHandler {

    private final ApplicationParams applicationParams;
    private final DeadLetterService deadLetterService;
    private final HearingStatusAuditService hearingStatusAuditService;
    private final HearingRepository hearingRepository;
    public static final String MESSAGE_PARSE_ERROR = "Unable to parse incoming message with id '{}'";
    public static final String APPLICATION_ERROR = "Unable to process incoming message with id '{}";
    public static final String MESSAGE_DEAD_LETTERED = "Message with id '{}' was dead lettered";
    public static final String NO_EXCEPTION_MESSAGE = "Exception message not found";
    public static final String RETRY_MESSAGE = "Retrying message with id '{}'";
    public static final String RETRIES_EXCEEDED = "Max delivery count reached. Message with id '{}' was dead lettered";

    @Autowired
    public ServiceBusMessageErrorHandler(DeadLetterService deadLetterService,
                                         ApplicationParams applicationParams,
                                         HearingStatusAuditService hearingStatusAuditService,
                                         HearingRepository hearingRepository) {
        this.deadLetterService = deadLetterService;
        this.applicationParams = applicationParams;
        this.hearingStatusAuditService = hearingStatusAuditService;
        this.hearingRepository = hearingRepository;
    }

    public void handleJsonError(ServiceBusReceivedMessageContext messageContext,
                                JsonProcessingException exception) {
        log.error(MESSAGE_PARSE_ERROR, messageContext.getMessage().getMessageId(), exception);
        messageContext.deadLetter(deadLetterService.handleParsingError(exception.getMessage()));
        logHearingAuditDetails(messageContext.getMessage(), exception.getMessage());
        log.warn(MESSAGE_DEAD_LETTERED, messageContext.getMessage().getMessageId());
    }

    public void handleApplicationError(ServiceBusReceivedMessageContext messageContext,
                                       Exception exception) {
        final Long deliveryCount = messageContext.getMessage().getRawAmqpMessage().getHeader().getDeliveryCount();
        if (deliveryCount >= applicationParams.getMaxRetryAttempts()) {
            log.error(APPLICATION_ERROR, messageContext.getMessage().getMessageId(), exception);
            messageContext.deadLetter(deadLetterService.handleApplicationError(exception.getMessage()));
            logHearingAuditDetails(messageContext.getMessage(), exception.getMessage());
            log.warn(RETRIES_EXCEEDED, messageContext.getMessage().getMessageId());
        } else {
            messageContext.abandon();
            log.warn(RETRY_MESSAGE, messageContext.getMessage().getMessageId());
            log.error(
                ERROR_PROCESSING_MESSAGE,
                HMC_HMI_OUTBOUND_ADAPTER,
                HMC_TO_HMI,
                READ,
                messageContext.getMessage().getApplicationProperties().getOrDefault(HEARING_ID, NOT_DEFINED)
            );
        }
    }

    public void handleGenericError(ServiceBusReceivedMessageContext messageContext,
                                   Exception exception) {
        log.error(APPLICATION_ERROR, messageContext.getMessage().getMessageId(), exception);
        if (exception.getMessage() != null) {
            messageContext.deadLetter(deadLetterService.handleApplicationError(exception.getMessage()));
            logHearingAuditDetails(messageContext.getMessage(), exception.getMessage());
        } else {
            messageContext.deadLetter(deadLetterService.handleApplicationError(NO_EXCEPTION_MESSAGE));
            logHearingAuditDetails(messageContext.getMessage(), NO_EXCEPTION_MESSAGE);
        }
        log.warn(MESSAGE_DEAD_LETTERED, messageContext.getMessage().getMessageId());
    }

    @SneakyThrows
    private void logHearingAuditDetails(ServiceBusReceivedMessage messageContextMessage, String exceptionMessage) {
        Map<String, Object> applicationProperties = messageContextMessage.getApplicationProperties();
        String hearingId = applicationProperties.get(HEARING_ID).toString();
        JsonNode errorDetails = new ObjectMapper().readTree("{\"deadLetterReason\": \""
                                                                + exceptionMessage + "\"}");
        Optional<HearingEntity> hearingEntity = hearingRepository.findById(Long.valueOf(hearingId));
        if (hearingEntity.isPresent()) {
            HearingStatusAuditContext hearingStatusAuditContext =
                HearingStatusAuditContext.builder()
                    .hearingEntity(hearingEntity.get())
                    .hearingEvent(HMC_TO_HMI_AUTH)
                    .httpStatus(FAILURE_STATUS)
                    .source(HMC)
                    .target(HMI)
                    .errorDetails(errorDetails)
                    .build();
            hearingStatusAuditService.saveAuditTriageDetails(hearingStatusAuditContext);
        }
    }

}

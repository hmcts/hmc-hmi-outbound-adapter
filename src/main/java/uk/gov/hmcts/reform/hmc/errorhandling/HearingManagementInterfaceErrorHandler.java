package uk.gov.hmcts.reform.hmc.errorhandling;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HearingManagementInterfaceErrorHandler {
    private final int retryAttempts = 2; //change this, gives DLQ with deliveryCount of 3
    private final DeadLetterService deadLetterService;
    public static final String MESSAGE_PARSE_ERROR = "Unable to parse incoming message with id '{}'";
    public static final String APPLICATION_ERROR = "Unable to process incoming message with id '{}";
    public static final String MESSAGE_DEAD_LETTERED = "Message with id '{}' was dead lettered";
    public static final String NO_EXCEPTION_MESSAGE = "Exception message not found";
    public static final String RETRY_MESSAGE = "Retrying message with id '{}'";
    public static final String RETRIES_EXCEEDED = "Max delivery count reached. Message with id '{}' was dead lettered";

    public HearingManagementInterfaceErrorHandler(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    public void handleJsonError(ServiceBusReceiverClient receiver,
                                ServiceBusReceivedMessage message,
                                JsonProcessingException exception) {
        log.error(MESSAGE_PARSE_ERROR, message.getMessageId(), exception);
        receiver.deadLetter(message, deadLetterService.handleParsingError(exception.getMessage()));
        log.warn(MESSAGE_DEAD_LETTERED, message.getMessageId());
    }

    public void handleApplicationError(ServiceBusReceiverClient receiver,
                                       ServiceBusReceivedMessage message,
                                       Exception exception) {
        final Long deliveryCount = message.getRawAmqpMessage().getHeader().getDeliveryCount();
        if (deliveryCount >= retryAttempts) {
            log.error(APPLICATION_ERROR, message.getMessageId(), exception);
            receiver.deadLetter(message, deadLetterService.handleApplicationError(exception.getMessage()));
            log.warn(RETRIES_EXCEEDED, message.getMessageId());
        } else {
            receiver.abandon(message);
            log.warn(RETRY_MESSAGE, message.getMessageId());
        }
    }

    public void handleGenericError(ServiceBusReceiverClient receiver,
                                   ServiceBusReceivedMessage message,
                                   Exception exception) {
        log.error(APPLICATION_ERROR, message.getMessageId(), exception);
        if (exception.getMessage() != null) {
            receiver.deadLetter(message, deadLetterService.handleApplicationError(exception.getMessage()));
        } else {
            receiver.deadLetter(message, deadLetterService.handleApplicationError(NO_EXCEPTION_MESSAGE));
        }
        log.warn(MESSAGE_DEAD_LETTERED, message.getMessageId());
    }
}

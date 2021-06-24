package uk.gov.hmcts.reform.hmc.errorhandling;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HearingManagementInterfaceErrorHandler {
    private final int retryAttempts = 3; //change this
    private final DeadLetterService deadLetterService;
    private static final String MESSAGE_PARSE_ERROR = "Unable to parse incoming message with id '{}'";
    private static final String APPLICATION_ERROR = "Unable to process incoming message with id '{}";

    public HearingManagementInterfaceErrorHandler(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    public void handleJsonError(ServiceBusReceiverClient receiver,
                                ServiceBusReceivedMessage message,
                                JsonProcessingException exception) {
        log.error(MESSAGE_PARSE_ERROR, message.getMessageId(), exception);
        String messageData = new String(message.getBody().toBytes());
        receiver.deadLetter(message, deadLetterService.handleParsingError(messageData, exception.getMessage()));
        log.warn("Message with id '{}' was dead lettered", message.getMessageId(), exception);
    }

    public void handleApplicationError(ServiceBusReceiverClient receiver,
                                       ServiceBusReceivedMessage message,
                                       Exception exception) {
        log.error(APPLICATION_ERROR, message.getMessageId(), exception);
        final Long deliveryCount = message.getRawAmqpMessage().getHeader().getDeliveryCount();
        if (deliveryCount >= retryAttempts) {
            String messageData = new String(message.getBody().toBytes());
            receiver.deadLetter(message, deadLetterService.handleApplicationError(messageData, exception.getMessage()));
            log.warn("Max delivery count reached. Message with id '{}' was dead lettered", message.getMessageId());
        } else {
            receiver.abandon(message);
            log.warn("Retrying message with id '{}'", message.getMessageId());
        }
    }

    public void handleGenericError(ServiceBusReceiverClient receiver,
                                   ServiceBusReceivedMessage message,
                                   Exception exception) {
        log.error(MESSAGE_PARSE_ERROR, message.getMessageId(), exception);
        String messageData = new String(message.getBody().toBytes());
        if (exception.getMessage() != null) {
            receiver.deadLetter(message, deadLetterService.handleApplicationError(messageData, exception.getMessage()));
        } else {
            receiver.deadLetter(message, deadLetterService.handleApplicationError(messageData, exception.getMessage()));
        }
        log.warn("Unknown Error. Message with id '{}' was dead lettered", message.getMessageId(), exception);
    }
}

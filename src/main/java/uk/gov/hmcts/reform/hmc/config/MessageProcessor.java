package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.Map;

@Slf4j
@Component
public class MessageProcessor {

    private final DefaultFutureHearingRepository futureHearingRepository;
    private final ObjectMapper objectMapper;
    private static final String CASE_LISTING_ID = "hearing_id";
    private static final String MESSAGE_TYPE = "message_type";

    public MessageProcessor(DefaultFutureHearingRepository futureHearingRepository,
                            @Qualifier("DefaultObjectMapper") ObjectMapper objectMapper) {
        this.futureHearingRepository = futureHearingRepository;
        this.objectMapper = objectMapper;
    }

    public void processMessage(ServiceBusReceiverClient client, ServiceBusReceivedMessage message) {
        try {
            log.info("Received message with id '{}'", message.getMessageId());
            MessageType messageType = null;
            if (message.getApplicationProperties().containsKey(MESSAGE_TYPE)) {
                messageType = MessageType.valueOf(message.getApplicationProperties().get(MESSAGE_TYPE).toString());
            }
            processMessage(convertMessage(message.getBody()),
                                            messageType, message.getApplicationProperties()
            );
            client.complete(message);
            log.info("Message with id '{}' handled successfully", message.getMessageId());
        } catch (RestClientException ex) {
            log.error(ex.getMessage());
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    public void processMessage(JsonNode message, MessageType messageType, Map<String, Object> applicationProperties) {
        if (messageType != null) {

            switch (messageType) {
                case REQUEST_HEARING:
                    log.debug("Message of type REQUEST_HEARING received");
                    futureHearingRepository.createHearingRequest(message);
                    break;
                case AMEND_HEARING:
                    log.debug("Message of type AMEND_HEARING received");
                    futureHearingRepository.amendHearingRequest(
                        message,
                        applicationProperties.get(CASE_LISTING_ID).toString()
                    );
                    break;
                case DELETE_HEARING:
                    log.debug("Message of type DELETE_HEARING received");
                    futureHearingRepository.deleteHearingRequest(
                        message,
                        applicationProperties.get(CASE_LISTING_ID).toString()
                    );
                    break;
                default:
                    log.info("Message has unsupported value for message_type");
                    // add to dead letter queue - unsupported message type
                    break;
            }
        } else {
            // add to dead letter queue - unsupported message type
            log.info("Message is missing custom header message_type");
        }

    }

    private JsonNode convertMessage(BinaryData message) throws JsonProcessingException {
        return objectMapper.readTree(message.toString());
    }
}

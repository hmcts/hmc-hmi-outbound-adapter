package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.Map;

import static uk.gov.hmcts.reform.hmc.config.MessageType.AMEND_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.DELETE_HEARING;

@Slf4j
@Component
public class MessageProcessor {

    private final ServiceBusMessageErrorHandler errorHandler;
    private final DefaultFutureHearingRepository futureHearingRepository;
    private final ObjectMapper objectMapper;
    private static final String HEARING_ID = "hearing_id";
    private static final String MESSAGE_TYPE = "message_type";
    public static final String MISSING_CASE_LISTING_ID = "Message is missing custom header hearing_id";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "Message has unsupported value for message_type";
    public static final String MISSING_MESSAGE_TYPE = "Message is missing custom header message_type";

    public MessageProcessor(DefaultFutureHearingRepository futureHearingRepository,
                            ServiceBusMessageErrorHandler errorHandler, ObjectMapper objectMapper) {
        this.errorHandler = errorHandler;
        this.futureHearingRepository = futureHearingRepository;
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
        } catch (AuthenticationException | ResourceNotFoundException ex) {
            errorHandler.handleApplicationError(client, message, ex);
        } catch (JsonProcessingException ex) {
            errorHandler.handleJsonError(client, message, ex);
        } catch (Exception ex) {
            log.warn("Unexpected Error");
            errorHandler.handleGenericError(client, message, ex);
        }
    }

    public void processMessage(JsonNode message, Map<String, Object> applicationProperties) {
        if (applicationProperties.containsKey(MESSAGE_TYPE)) {
            MessageType messageType;
            try {
                messageType =
                    MessageType.valueOf(applicationProperties.get(MESSAGE_TYPE).toString());
            } catch (Exception exception) {
                throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }
            String caseListingID = null;
            if (messageType.equals(AMEND_HEARING) || messageType.equals(DELETE_HEARING)) {
                try {
                    caseListingID = applicationProperties.get(HEARING_ID).toString();
                } catch (Exception exception) {
                    throw new MalformedMessageException(MISSING_CASE_LISTING_ID);
                }
            }

            switch (messageType) {
                case REQUEST_HEARING:
                    log.debug("Message of type REQUEST_HEARING received");
                    HearingManagementInterfaceResponse response = futureHearingRepository.createHearingRequest(message);
                    log.info("PRINTING RESPONSE FROM HMI: " + String.valueOf(response.getResponseCode()) + "| " +
                        response.getDescription());
                    break;
                case AMEND_HEARING:
                    log.debug("Message of type AMEND_HEARING received");
                    futureHearingRepository.amendHearingRequest(
                        message, caseListingID
                    );
                    break;
                case DELETE_HEARING:
                    log.debug("Message of type DELETE_HEARING received");
                    futureHearingRepository.deleteHearingRequest(
                        message, caseListingID
                    );
                    break;
                default:
                    throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }

        } else {
            throw new MalformedMessageException(MISSING_MESSAGE_TYPE);
        }
    }

    private JsonNode convertMessage(BinaryData message) throws JsonProcessingException {
        return objectMapper.readTree(message.toString());
    }
}

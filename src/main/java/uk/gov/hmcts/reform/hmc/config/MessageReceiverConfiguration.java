package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.HearingManagementInterfaceErrorHandler;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.time.Duration;
import javax.annotation.PostConstruct;

import static uk.gov.hmcts.reform.hmc.config.MessageType.AMEND_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.DELETE_HEARING;

@Slf4j
@Component
public class MessageReceiverConfiguration implements Runnable {

    private final HearingManagementInterfaceErrorHandler handler;
    private final ApplicationParams applicationParams;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final HearingManagementInterfaceApiClient hmiClient;
    private static final String MESSAGE_TYPE = "message_type";
    private String caseListingID;
    private static final String MISSING_CASE_LISTING_ID = "Message is missing custom header caseListingID";
    private static final String UNSUPPORTED_MESSAGE_TYPE = "Message has unsupported value for message_type";
    private static final String MISSING_MESSAGE_TYPE = "Message is missing custom header message_type";

    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();

    public MessageReceiverConfiguration(ApplicationParams applicationParams,
                                        ActiveDirectoryApiClient activeDirectoryApiClient,
                                        HearingManagementInterfaceApiClient hmiClient,
                                        HearingManagementInterfaceErrorHandler handler
    ) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
        this.handler = handler;
    }

    @Override
    @SuppressWarnings("squid:S2189")
    @PostConstruct
    public void run() {
        log.info("Creating Session receiver");

        ServiceBusReceiverClient client = new ServiceBusClientBuilder()
            .connectionString(applicationParams.getConnectionString())
            .retryOptions(retryOptions())
            .receiver()
            .queueName(applicationParams.getQueueName())
            .buildClient();

        while (true) {
            receiveMessages(client);
        }
    }

    // handles received messages
    public void receiveMessages(ServiceBusReceiverClient client) {

        client.receiveMessages(1)
            .forEach(
                message -> {
                    try {
                        log.info("Received message with id '{}'", message.getMessageId());
                        processMessage(message);
                        client.complete(message);
                        log.info("Message with id '{}' handled successfully", message.getMessageId());
                    } catch (MalformedMessageException ex) {
                        handler.handleGenericError(client, message, ex);
                    } catch (AuthenticationException | ResourceNotFoundException ex) {
                        handler.handleApplicationError(client, message, ex);
                    } catch (Exception ex) {
                        log.warn("Unexpected Error");
                        handler.handleGenericError(client, message, ex);
                    }
                });
    }

    private AmqpRetryOptions retryOptions() {
        AmqpRetryOptions retryOptions = new AmqpRetryOptions();
        retryOptions.setTryTimeout(Duration.ofSeconds(Integer.valueOf(applicationParams.getWaitToRetryTime())));

        return retryOptions;
    }

    private static JsonNode convertMessage(BinaryData message) {
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return OBJECT_MAPPER.convertValue(message, JsonNode.class);
    }

    private void processMessage(ServiceBusReceivedMessage message) {
        if (message.getApplicationProperties().containsKey(MESSAGE_TYPE)) {
            JsonNode node = convertMessage(message.getBody());

            MessageType messageType;
            try {
                messageType =
                    MessageType.valueOf(message.getApplicationProperties().get(MESSAGE_TYPE).toString());
            } catch (Exception exception) {
                throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }

            DefaultFutureHearingRepository defaultFutureHearingRepository =
                new DefaultFutureHearingRepository(
                    activeDirectoryApiClient,
                    applicationParams,
                    hmiClient
                );

            if (messageType.equals(AMEND_HEARING) || messageType.equals(DELETE_HEARING)) {
                try {
                    caseListingID = message.getApplicationProperties().get("caseListingID").toString();
                } catch (Exception exception) {
                    throw new MalformedMessageException(MISSING_CASE_LISTING_ID);
                }
            }

            switch (messageType) {
                case REQUEST_HEARING:
                    log.info("Message of type REQUEST_HEARING received");
                    defaultFutureHearingRepository.createHearingRequest(node);
                    break;
                case AMEND_HEARING:
                    log.info("Message of type AMEND_HEARING received");
                    defaultFutureHearingRepository.amendHearingRequest(
                        node, caseListingID
                    );
                    break;
                case DELETE_HEARING:
                    log.info("Message of type DELETE_HEARING received");
                    defaultFutureHearingRepository.deleteHearingRequest(
                        node, caseListingID
                    );
                    break;
                default:
                    //should not get here as no more MessageType constants
            }
        } else {
            throw new MalformedMessageException(MISSING_MESSAGE_TYPE);
        }
    }
}

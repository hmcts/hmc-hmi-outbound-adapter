package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class MessageReceiverConfiguration implements Runnable {

    private final ApplicationParams applicationParams;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final MessageProcessor messageProcessor;
    private final HearingManagementInterfaceApiClient hmiClient;
    private static final String MESSAGE_TYPE = "message_type";

    public MessageReceiverConfiguration(ApplicationParams applicationParams,
                                        ActiveDirectoryApiClient activeDirectoryApiClient,
                                        HearingManagementInterfaceApiClient hmiClient,
                                        MessageProcessor messageProcessor) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.messageProcessor = messageProcessor;
        this.hmiClient = hmiClient;
    }

    @Override
    @SuppressWarnings("squid:S2189")
    @PostConstruct
    public void run() {
        log.info("Creating service bus receiver client");

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
                        MessageType messageType = null;
                        if (message.getApplicationProperties().containsKey(MESSAGE_TYPE)) {
                            MessageType.valueOf(message.getApplicationProperties().get(MESSAGE_TYPE).toString());
                        }
                        messageProcessor.processMessage(convertMessage(message.getBody()),
                                                        messageType, message.getApplicationProperties());
                        client.complete(message);
                        log.info("Message with id '{}' handled successfully", message.getMessageId());
                    } catch (RestClientException ex) {
                        log.error(ex.getMessage());
                    } catch (Exception ex) {
                        log.error(ex.getMessage());
                    }
                });
    }

    private AmqpRetryOptions retryOptions() {
        AmqpRetryOptions retryOptions = new AmqpRetryOptions();
        retryOptions.setMaxRetries(Integer.valueOf(applicationParams.getMaxRetryAttempts()));

        return retryOptions;
    }

    private static JsonNode convertMessage(BinaryData message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(message.toString());
    }
}

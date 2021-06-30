package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

@Slf4j
@Component
public class MessageReceiverConfiguration implements CommandLineRunner {

    private final ApplicationParams applicationParams;
    private final MessageProcessor messageProcessor;

    public MessageReceiverConfiguration(ApplicationParams applicationParams,
                                        MessageProcessor messageProcessor) {
        this.applicationParams = applicationParams;
        this.messageProcessor = messageProcessor;
    }

    @Override
    @SuppressWarnings("squid:S2189")
    public void run(String... args) {
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
                    messageProcessor.processMessage(client, message);
                });
    }

    private AmqpRetryOptions retryOptions() {
        AmqpRetryOptions retryOptions = new AmqpRetryOptions();
        retryOptions.setMaxRetries(Integer.valueOf(applicationParams.getMaxRetryAttempts()));

        return retryOptions;
    }
}

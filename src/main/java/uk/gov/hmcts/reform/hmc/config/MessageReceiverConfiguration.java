package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.amqp.AmqpRetryMode;
import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.service.MessageProcessor;

import java.time.Duration;

@Slf4j
@Component
public class MessageReceiverConfiguration {

    private final ApplicationParams applicationParams;
    private final MessageProcessor messageProcessor;

    public MessageReceiverConfiguration(ApplicationParams applicationParams,
                                        MessageProcessor messageProcessor) {
        this.applicationParams = applicationParams;
        this.messageProcessor = messageProcessor;
    }

    @Async
    @EventListener(ApplicationStartedEvent.class)
    @SuppressWarnings("squid:S2189")
    public void run() {
        log.info("Creating service bus receiver client");

        ServiceBusReceiverClient client = new ServiceBusClientBuilder()
            .connectionString(applicationParams.getOutboundConnectionString())
            .retryOptions(retryOptions())
            .receiver()
            .queueName(applicationParams.getOutboundQueueName())
            .buildClient();

        while (true) {
            receiveMessages(client);
        }
    }

    // handles received messages
    public void receiveMessages(ServiceBusReceiverClient client) {

        client.receiveMessages(1)
            .forEach(
                message -> messageProcessor.processMessage(client, message));
    }

    private AmqpRetryOptions retryOptions() {
        AmqpRetryOptions retryOptions = new AmqpRetryOptions();
        retryOptions.setMode(AmqpRetryMode.EXPONENTIAL)
            .setDelay(Duration.ofSeconds(Long.valueOf(applicationParams.getExponentialMultiplier())));
        return retryOptions;
    }
}

package uk.gov.hmcts.reform.hmc.config;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@AutoConfigureAfter(QueueClientConfig.class)

@Slf4j
@Configuration
public class MessageReceiverConfiguration {

    private ServiceBusProcessorClient processedMessagesQueueClient;

    MessageReceiverConfiguration(ServiceBusProcessorClient processedMessagesQueueClient) {
        this.processedMessagesQueueClient = processedMessagesQueueClient;
    }

    @PostConstruct()
    public void registerMessageHandlers() {
        log.info("Registering service bus processor client");
        processedMessagesQueueClient.start();
    }

}

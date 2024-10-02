package uk.gov.hmcts.reform.hmc.config;


import com.azure.core.util.ConfigurationBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import static uk.gov.hmcts.reform.hmc.constants.Constants.AMQP_CACHE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.AMQP_CACHE_VALUE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_PROCESSING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_FROM_HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_HMI_OUTBOUND_ADAPTER;
import static uk.gov.hmcts.reform.hmc.constants.Constants.WRITE;

@Slf4j
@Component
public class MessageSenderConfiguration {

    private final ApplicationParams applicationParams;

    private static final String MESSAGE_TYPE = "message_type";
    private static final String HEARING_ID = "hearing_id";

    public MessageSenderConfiguration(ApplicationParams applicationParams) {
        this.applicationParams = applicationParams;
    }

    public void sendMessage(String message, String messageType, String hearingId) {
        try {
            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message);
            serviceBusMessage.getApplicationProperties().put(MESSAGE_TYPE, messageType);
            serviceBusMessage.getApplicationProperties().put(HEARING_ID, hearingId);
            ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                .connectionString(applicationParams.getInboundConnectionString())
                .configuration(new ConfigurationBuilder()
                                   .putProperty(AMQP_CACHE, AMQP_CACHE_VALUE)
                                   .build())
                .sender()
                .queueName(applicationParams.getInboundQueueName())
                .buildClient();
            log.debug("Connected to Queue {}", applicationParams.getOutboundQueueName());
            senderClient.sendMessage(serviceBusMessage);
            log.debug("Message has been sent to the Queue {}, hearingId {}, messageType {}",
                      applicationParams.getOutboundQueueName(), hearingId, messageType);
        } catch (Exception e) {
            log.error("Error while sending the message to queue:{}", e.getMessage());
            log.error(
                ERROR_PROCESSING_MESSAGE,
                HMC_HMI_OUTBOUND_ADAPTER,
                HMC_FROM_HMI,
                WRITE,
                hearingId
            );
        }
    }
}

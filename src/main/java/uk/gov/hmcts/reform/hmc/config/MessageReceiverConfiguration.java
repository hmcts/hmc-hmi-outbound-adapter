package uk.gov.hmcts.reform.hmc.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusFailureReason;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MessageReceiverConfiguration {

    private static ApplicationParams applicationParams;
    private static final String REQUEST_HEARING = "REQUEST_HEARING";
    private static final String MESSAGE_TYPE = "message_type";

    public MessageReceiverConfiguration(ApplicationParams applicationParams) {
        this.applicationParams = applicationParams;
    }

    // handles received messages
    public void receiveMessages() throws InterruptedException {
        CountDownLatch countdownLatch = new CountDownLatch(1);

        // Create an instance of the processor through the ServiceBusClientBuilder
        ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
            .connectionString(applicationParams.getConnectionString())
            .processor()
            .queueName(applicationParams.getQueueName())
            .processMessage(MessageReceiverConfiguration::processMessage)
            .processError(context -> processError(context, countdownLatch))
            .buildProcessorClient();
        log.info("Connected to Queue");

        log.info("Starting the processor");
        processorClient.start();

        //to be removed
        TimeUnit.SECONDS.sleep(Long.valueOf(applicationParams.getWaitToRetryTime()));
        log.info("Stopping and closing the processor");
        processorClient.close();
    }

    private static void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();
        log.info("Processing message. Session: %s, Sequence #: %s. Contents: %s%n", message.getMessageId(),
                 message.getSequenceNumber(), message.getBody());

        if (message.getApplicationProperties().containsKey(MESSAGE_TYPE)) {
            switch (message.getApplicationProperties().get(MESSAGE_TYPE).toString()) {
                case REQUEST_HEARING:
                    log.info("Message of type REQUEST_HEARING received");
                    // calls Abi's code;
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

    private static void processError(ServiceBusErrorContext context, CountDownLatch countdownLatch) {
        log.error("Error when receiving messages from namespace: '%s'. Entity: '%s'%n",
                          context.getFullyQualifiedNamespace(), context.getEntityPath()
        );

        if (!(context.getException() instanceof ServiceBusException)) {
            log.error("Non-ServiceBusException occurred: %s%n", context.getException());
            return;
        }

        ServiceBusException exception = (ServiceBusException) context.getException();
        ServiceBusFailureReason reason = exception.getReason();

        if (reason == ServiceBusFailureReason.MESSAGING_ENTITY_DISABLED
            || reason == ServiceBusFailureReason.MESSAGING_ENTITY_NOT_FOUND
            || reason == ServiceBusFailureReason.UNAUTHORIZED) {
            log.error("An unrecoverable error occurred. Stopping processing with reason %s: %s%n",
                              reason, exception.getMessage()
            );

            countdownLatch.countDown();
        } else if (reason == ServiceBusFailureReason.MESSAGE_LOCK_LOST) {
            log.error("Message lock lost for message: %s%n", context.getException());
        } else if (reason == ServiceBusFailureReason.SERVICE_BUSY) {
            try {
                // Choosing an arbitrary amount of time to wait until trying again.
                TimeUnit.SECONDS.sleep(Long.valueOf(applicationParams.getWaitToRetryTime()));
            } catch (InterruptedException e) {
                log.error("Unable to sleep for period of time");
            }
        } else {
            log.error("Error source %s, reason %s, message: %s%n", context.getErrorSource(),
                              reason, context.getException()
            );
        }
    }
}

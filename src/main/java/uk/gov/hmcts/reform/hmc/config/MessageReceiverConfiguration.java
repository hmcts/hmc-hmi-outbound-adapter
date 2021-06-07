package uk.gov.hmcts.reform.hmc.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusFailureReason;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class MessageReceiverConfiguration {

    private static Logger logger;
    private static final String CONNECTION_STRING_KEY = "CONNECTION_STRING";

    // handles received messages
    static void receiveMessages() throws InterruptedException
    {
        CountDownLatch countdownLatch = new CountDownLatch(1);

        // Create an instance of the processor through the ServiceBusClientBuilder
        ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
            .connectionString(System.getenv(CONNECTION_STRING_KEY))
            .processor()
            .queueName("hmc-to-hmi-env")
            .processMessage(MessageReceiverConfiguration::processMessage)
            .processError(context -> processError(context, countdownLatch))
            .buildProcessorClient();
        logger.info("Connected to Queue");

        logger.info("Starting the processor");
        processorClient.start();

        TimeUnit.SECONDS.sleep(10);
        logger.info("Stopping and closing the processor");
        processorClient.close();
    }

    private static void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();
        logger.info("Processing message. Session: %s, Sequence #: %s. Contents: %s%n", message.getMessageId(),
                          message.getSequenceNumber(), message.getBody());
    }

    private static void processError(ServiceBusErrorContext context, CountDownLatch countdownLatch) {
        logger.error("Error when receiving messages from namespace: '%s'. Entity: '%s'%n",
                          context.getFullyQualifiedNamespace(), context.getEntityPath()
        );

        if (!(context.getException() instanceof ServiceBusException)) {
            logger.error("Non-ServiceBusException occurred: %s%n", context.getException());
            return;
        }

        ServiceBusException exception = (ServiceBusException) context.getException();
        ServiceBusFailureReason reason = exception.getReason();

        if (reason == ServiceBusFailureReason.MESSAGING_ENTITY_DISABLED
            || reason == ServiceBusFailureReason.MESSAGING_ENTITY_NOT_FOUND
            || reason == ServiceBusFailureReason.UNAUTHORIZED) {
            logger.error("An unrecoverable error occurred. Stopping processing with reason %s: %s%n",
                              reason, exception.getMessage()
            );

            countdownLatch.countDown();
        } else if (reason == ServiceBusFailureReason.MESSAGE_LOCK_LOST) {
            logger.error("Message lock lost for message: %s%n", context.getException());
        } else if (reason == ServiceBusFailureReason.SERVICE_BUSY) {
            try {
                // Choosing an arbitrary amount of time to wait until trying again.
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                logger.error("Unable to sleep for period of time");
            }
        } else {
            logger.error("Error source %s, reason %s, message: %s%n", context.getErrorSource(),
                              reason, context.getException()
            );
        }


    }
}

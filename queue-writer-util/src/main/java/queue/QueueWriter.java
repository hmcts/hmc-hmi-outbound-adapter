package queue;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For writing single message to an Azure Service Bus queue
 */
public class QueueWriter {

    private static final Logger logger = LoggerFactory.getLogger(QueueWriter.class);

    private static final String CONNECTION_STRING_KEY = "CONNECTION_STRING";
    private static final String QUEUE_NAME = "QUEUE";
    private static final String MESSAGE_BODY = "MESSAGE";

    private static final String[] REQUIRED_ENV = {CONNECTION_STRING_KEY, QUEUE_NAME, MESSAGE_BODY};

    public static void main(String[] args) {
        logger.info("Starting queue writer util");
        verifyEnv();
        writeToQueue();
    }

    private static void verifyEnv() {
        for (String env : REQUIRED_ENV) {
            if (System.getenv(env) == null) {
                logger.error("Environment variable {} is required", env);
                System.exit(1);
            }
        }
    }

    private static void writeToQueue() {

        // create a Service Bus Sender client for the queue
        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
            .connectionString(System.getenv(CONNECTION_STRING_KEY))
            .sender()
            .queueName(System.getenv(QUEUE_NAME))
            .buildClient();

        // send one message to the queue
        senderClient.sendMessage(new ServiceBusMessage("{\n" +
                                                           "  \"request\": {\n" +
                                                           "    \"testing\":\"value\"\n" +
                                                           "  }\n" +
                                                           "}\n"));
        logger.info("Sent a single message to the queue: " + System.getenv(QUEUE_NAME));
    }
}

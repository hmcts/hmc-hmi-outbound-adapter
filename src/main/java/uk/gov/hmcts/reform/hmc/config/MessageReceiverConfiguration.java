package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusFailureReason;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.errorhandling.HearingManagementInterfaceErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.FutureHearingRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.REQUEST_NOT_FOUND;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;

@Slf4j
@Component
public class MessageReceiverConfiguration {

    private static HearingManagementInterfaceErrorHandler handler;
    private static FutureHearingRepository repository;
    private static final Exception exception = new Exception("Test message");
    private final ApplicationParams applicationParams;
    private static final String REQUEST_HEARING = "REQUEST_HEARING";
    private static final String AMEND_HEARING = "AMEND_HEARING";
    private static final String DELETE_HEARING = "DELETE_HEARING";
    private static final String MESSAGE_TYPE = "message_type";
    private static final String HEARING_ID = "hearing_id";
    private static JsonNode data = null;

    public MessageReceiverConfiguration(ApplicationParams applicationParams,
                                        HearingManagementInterfaceErrorHandler handler,
                                        FutureHearingRepository repository) {
        this.applicationParams = applicationParams;
        this.handler = handler;
        this.repository = repository;
    }

    // handles received messages
    public void receiveMessages() {
        CountDownLatch countdownLatch = new CountDownLatch(1);
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
    }

    private static JsonNode convertMessage(BinaryData message) {
        JsonNode newNode = null;
        try {
            ObjectMapper om = new ObjectMapper();
            final ObjectWriter writer = om.writer();
            final byte[] bytes = writer.writeValueAsBytes(message);
            final ObjectReader reader = om.reader();
            newNode = reader.readTree(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            log.error(exception.getMessage());
            //genericErrorHandlerCall
        }
        return newNode;
    }

    private static void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();
        log.info("Processing message. Session: %s, Sequence #: %s. Contents: %s%n", message.getMessageId(),
                 message.getSequenceNumber(), message.getBody()
        );
        data = convertMessage(message.getBody());
        try {
            if (message.getApplicationProperties().containsKey(MESSAGE_TYPE)) {


                switch (message.getApplicationProperties().get(MESSAGE_TYPE).toString()) {
                    case REQUEST_HEARING:
                        log.info("Message of type REQUEST_HEARING received");
                        repository.createHearingRequest(data);
                        break;
                    case AMEND_HEARING:
                        log.info("Message of type AMEND_HEARING received");
                        if (message.getApplicationProperties().containsKey(HEARING_ID)) {
                            repository.amendHearingRequest(
                                data,
                                message.getApplicationProperties().get(HEARING_ID).toString()
                            );
                        } else {
                            // genericErrorHandlerCall - unsupported message type
                            log.info("Message is missing custom header message_type");
                        }
                        break;
                    case DELETE_HEARING:
                        log.info("Message of type DELETE_HEARING received");
                        if (message.getApplicationProperties().containsKey(HEARING_ID)) {
                            repository.deleteHearingRequest(
                                data,
                                message.getApplicationProperties().get(HEARING_ID).toString()
                            );
                        }
                        break;
                    default:
                        log.info("Message has unsupported value for message_type");
                        handler.handleGenericError(context, message, exception);
                        break;
                }
            } else {
                // genericErrorHandlerCall - unsupported message type
                log.info("Message is missing custom header message_type");
            }
        } catch (Exception exception) {

            switch (exception.getMessage()) {
                case INVALID_REQUEST:
                    handler.handleApplicationError(context, message, exception);
                    break;
                case INVALID_SECRET:
                    handler.handleApplicationError(context, message, exception);
                    break;
                case REQUEST_NOT_FOUND:
                    handler.handleApplicationError(context, message, exception);
                    break;
                case SERVER_ERROR:
                    //genericErrorHandlerCall
                    break;
                default:
                    //generic
            }
        }

    }

    private void processError(ServiceBusErrorContext context, CountDownLatch countdownLatch) {
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
                // wait an arbitrary amount of time to wait until trying again
                String value = applicationParams.getWaitToRetryTime();
                TimeUnit.SECONDS.sleep(Long.valueOf(value));
            } catch (InterruptedException e) {
                log.error("Unable to sleep for period of time");
                Thread.currentThread().interrupt();
            }
        } else {
            log.error("Error source %s, reason %s, message: %s%n", context.getErrorSource(),
                      reason, context.getException()
            );
        }
    }
}

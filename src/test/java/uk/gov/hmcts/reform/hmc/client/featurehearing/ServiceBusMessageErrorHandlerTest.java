package uk.gov.hmcts.reform.hmc.client.featurehearing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.azure.core.amqp.models.AmqpAnnotatedMessage;
import com.azure.core.amqp.models.AmqpMessageHeader;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.api.mockito.PowerMockito;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.config.MessageType;
import uk.gov.hmcts.reform.hmc.errorhandling.DeadLetterService;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.hmc.errorhandling.DeadLetterService.APPLICATION_PROCESSING_ERROR;
import static uk.gov.hmcts.reform.hmc.errorhandling.DeadLetterService.MESSAGE_DESERIALIZATION_ERROR;
import static uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler.APPLICATION_ERROR;
import static uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler.MESSAGE_DEAD_LETTERED;
import static uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler.MESSAGE_PARSE_ERROR;
import static uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler.NO_EXCEPTION_MESSAGE;
import static uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler.RETRIES_EXCEEDED;
import static uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler.RETRY_MESSAGE;

@ExtendWith(MockitoExtension.class)
class ServiceBusMessageErrorHandlerTest {

    @Mock
    private DeadLetterService deadLetterService;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ServiceBusReceiverClient receiverClient;

    @Mock
    private ServiceBusReceivedMessageContext messageContext = mock(ServiceBusReceivedMessageContext.class);

    @Mock
    private ServiceBusReceivedMessage receivedMessage;

    @Mock
    private JsonProcessingException jsonProcessingException;

    @Mock
    private Exception exception;

    @Mock
    private AmqpMessageHeader amqpHeader;

    @Mock
    private AmqpAnnotatedMessage amqpAnnotatedMessage;

    @Mock
    private HearingStatusAuditService hearingStatusAuditService;

    @Mock
    HearingRepository hearingRepository;

    private ServiceBusMessageErrorHandler handler;
    private DeadLetterOptions deadLetterOptions;
    private static final String MESSAGE_ID = "1234567";
    private static final String ERROR_MESSAGE = "This is a test error message";
    private static final String HEARING_ID = "hearing_id";
    private static final String MESSAGE_TYPE = "message_type";
    Map<String, Object> applicationProperties = new HashMap<>();

    @BeforeEach
    void setUp() {
        handler = new ServiceBusMessageErrorHandler(deadLetterService, applicationParams, hearingStatusAuditService,
                                                    hearingRepository);
        deadLetterOptions = new DeadLetterOptions();
        deadLetterOptions.setDeadLetterErrorDescription(ERROR_MESSAGE);
        applicationProperties.put(HEARING_ID, "1234567890");
        applicationProperties.put(MESSAGE_TYPE, MessageType.REQUEST_HEARING);
    }

    @Test
    void shouldHandleJsonError() {
        deadLetterOptions.setDeadLetterReason(MESSAGE_DESERIALIZATION_ERROR);
        deadLetterOptions.setDeadLetterErrorDescription(ERROR_MESSAGE);

        Logger logger = (Logger) LoggerFactory.getLogger(ServiceBusMessageErrorHandler.class);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        given(messageContext.getMessage()).willReturn(receivedMessage);
        given(messageContext.getMessage().getMessageId()).willReturn(MESSAGE_ID);
        PowerMockito.when(messageContext.getMessage().getApplicationProperties()).thenReturn(applicationProperties);
        when(jsonProcessingException.getMessage()).thenReturn(ERROR_MESSAGE);
        when(deadLetterService.handleParsingError(ERROR_MESSAGE)).thenReturn(deadLetterOptions);
        doNothing().when(messageContext).deadLetter(deadLetterOptions);

        handler.handleJsonError(messageContext, jsonProcessingException);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(Level.WARN, logsList.get(1)
            .getLevel());

        assertTrue(logsList.get(0).getMessage().contains(MESSAGE_PARSE_ERROR));
        assertTrue(logsList.get(1).getMessage().contains(MESSAGE_DEAD_LETTERED));

        verify(deadLetterService, Mockito.times(1))
            .handleParsingError(ERROR_MESSAGE);
    }

    @Test
    void shouldHandleApplicationErrorWithRetry() {

        deadLetterOptions.setDeadLetterReason(APPLICATION_PROCESSING_ERROR);
        deadLetterOptions.setDeadLetterErrorDescription(ERROR_MESSAGE);

        Logger logger = (Logger) LoggerFactory.getLogger(ServiceBusMessageErrorHandler.class);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        given(messageContext.getMessage()).willReturn(receivedMessage);
        when(messageContext.getMessage().getRawAmqpMessage()).thenReturn(amqpAnnotatedMessage);
        PowerMockito.when(messageContext.getMessage().getApplicationProperties()).thenReturn(applicationProperties);
        when(amqpAnnotatedMessage.getHeader()).thenReturn(amqpHeader);
        when(amqpHeader.getDeliveryCount()).thenReturn(1L);
        when(applicationParams.getMaxRetryAttempts()).thenReturn(2);
        when(receivedMessage.getMessageId()).thenReturn(MESSAGE_ID);

        handler.handleApplicationError(messageContext, exception);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals(Level.WARN, logsList.get(0)
            .getLevel());

        assertTrue(logsList.get(0).getMessage().contains(RETRY_MESSAGE));

        verify(deadLetterService, Mockito.times(0))
            .handleApplicationError(ERROR_MESSAGE);
    }

    @Test
    void shouldHandleApplicationErrorWithDeadLetterQueue() {

        deadLetterOptions.setDeadLetterReason(APPLICATION_PROCESSING_ERROR);
        deadLetterOptions.setDeadLetterErrorDescription(ERROR_MESSAGE);

        Logger logger = (Logger) LoggerFactory.getLogger(ServiceBusMessageErrorHandler.class);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        given(messageContext.getMessage()).willReturn(receivedMessage);
        when(messageContext.getMessage().getRawAmqpMessage()).thenReturn(amqpAnnotatedMessage);
        PowerMockito.when(messageContext.getMessage().getApplicationProperties()).thenReturn(applicationProperties);
        when(amqpAnnotatedMessage.getHeader()).thenReturn(amqpHeader);
        when(amqpHeader.getDeliveryCount()).thenReturn(2L);
        when(applicationParams.getMaxRetryAttempts()).thenReturn(2);
        when(receivedMessage.getMessageId()).thenReturn(MESSAGE_ID);

        when(exception.getMessage()).thenReturn(ERROR_MESSAGE);
        when(deadLetterService.handleApplicationError(ERROR_MESSAGE)).thenReturn(deadLetterOptions);
        doNothing().when(messageContext).deadLetter(deadLetterOptions);

        handler.handleApplicationError(messageContext, exception);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(Level.WARN, logsList.get(1)
            .getLevel());
        assertTrue(logsList.get(0).getMessage().contains(APPLICATION_ERROR));
        assertTrue(logsList.get(1).getMessage().contains(RETRIES_EXCEEDED));

        verify(deadLetterService, Mockito.times(1))
            .handleApplicationError(ERROR_MESSAGE);
    }

    @Test
    void shouldHandleGenericErrorWithMessage() {

        deadLetterOptions.setDeadLetterReason(APPLICATION_PROCESSING_ERROR);
        deadLetterOptions.setDeadLetterErrorDescription(ERROR_MESSAGE);

        Logger logger = (Logger) LoggerFactory.getLogger(ServiceBusMessageErrorHandler.class);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        given(messageContext.getMessage()).willReturn(receivedMessage);
        given(messageContext.getMessage().getMessageId()).willReturn(MESSAGE_ID);
        PowerMockito.when(messageContext.getMessage().getApplicationProperties()).thenReturn(applicationProperties);
        when(exception.getMessage()).thenReturn(ERROR_MESSAGE);
        when(deadLetterService.handleApplicationError(ERROR_MESSAGE)).thenReturn(deadLetterOptions);
        doNothing().when(messageContext).deadLetter(deadLetterOptions);

        handler.handleGenericError(messageContext, exception);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(Level.WARN, logsList.get(1)
            .getLevel());
        assertTrue(logsList.get(0).getMessage().contains(APPLICATION_ERROR));
        assertTrue(logsList.get(1).getMessage().contains(MESSAGE_DEAD_LETTERED));

        verify(deadLetterService, Mockito.times(1))
            .handleApplicationError(ERROR_MESSAGE);
    }

    @Test
    void shouldHandleGenericErrorWithoutMessage() {

        deadLetterOptions.setDeadLetterReason(APPLICATION_PROCESSING_ERROR);
        deadLetterOptions.setDeadLetterErrorDescription(NO_EXCEPTION_MESSAGE);

        Logger logger = (Logger) LoggerFactory.getLogger(ServiceBusMessageErrorHandler.class);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        given(messageContext.getMessage()).willReturn(receivedMessage);
        given(messageContext.getMessage().getMessageId()).willReturn(MESSAGE_ID);
        PowerMockito.when(messageContext.getMessage().getApplicationProperties()).thenReturn(applicationProperties);
        when(exception.getMessage()).thenReturn(null);
        when(deadLetterService.handleApplicationError(NO_EXCEPTION_MESSAGE)).thenReturn(deadLetterOptions);
        doNothing().when(messageContext).deadLetter(deadLetterOptions);

        handler.handleGenericError(messageContext, exception);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(Level.WARN, logsList.get(1)
            .getLevel());
        assertTrue(logsList.get(0).getMessage().contains(APPLICATION_ERROR));
        assertTrue(logsList.get(1).getMessage().contains(MESSAGE_DEAD_LETTERED));

        verify(deadLetterService, Mockito.times(1))
            .handleApplicationError(NO_EXCEPTION_MESSAGE);
    }

}

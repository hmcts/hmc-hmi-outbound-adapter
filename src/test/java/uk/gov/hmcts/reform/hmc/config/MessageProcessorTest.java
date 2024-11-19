package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.JsonProcessingRuntimeException;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;
import uk.gov.hmcts.reform.hmc.service.MessageProcessor;
import uk.gov.hmcts.reform.hmc.service.PendingRequestService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageProcessorTest {

    private MessageProcessor messageProcessor;
    private static final String HEARING_ID = "hearing_id";
    private static final String MESSAGE_TYPE = "message_type";

    @Mock
    private static DefaultFutureHearingRepository futureHearingRepository;

    @Mock
    private PendingRequestService pendingRequestService;

    @Mock
    private MessageSenderConfiguration messageSenderConfiguration;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Mock
    private ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);

    @Mock
    private ServiceBusMessageErrorHandler errorHandler;

    private JsonNode anyData;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        messageProcessor = new MessageProcessor(
                futureHearingRepository, errorHandler,
                messageSenderConfiguration,
                objectMapper,
                pendingRequestService);
        anyData = objectMapper.convertValue("test data", JsonNode.class);
        String requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        when(applicationParams.getGrantType()).thenReturn("GRANT_TYPE");
        when(applicationParams.getClientId()).thenReturn("CLIENT_ID");
        when(applicationParams.getScope()).thenReturn("SCOPE");
        when(applicationParams.getClientSecret()).thenReturn("CLIENT_SECRET");
        when(activeDirectoryApiClient.authenticate(requestString)).thenReturn(new AuthenticationResponse());
    }

    @ParameterizedTest
    @MethodSource("provideMessageTypes")
    void shouldInitiateHearing(String messageType, Runnable verifyMethod) {
        Map<String, Object> applicationProperties = Map.of(
            HEARING_ID, "1234567890",
            MESSAGE_TYPE, messageType
        );
        Mockito.when(message.getApplicationProperties()).thenReturn(applicationProperties);
        Mockito.when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        assertDoesNotThrow(() -> messageProcessor.processMessage(anyData, applicationProperties));
        verifyMethod.run();
    }

    @Test
    void shouldThrowErrorWhenMessageTypeIsNull() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, null);
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MessageProcessor.UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenCannotConvertMessage() {
        Map<String, Object> applicationProperties = new HashMap<>();
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("invalid data"));
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
                .isInstanceOf(MalformedMessageException.class)
                .hasMessageContaining(MessageProcessor.MISSING_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoMessageType() {
        Map<String, Object> applicationProperties = new HashMap<>();
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MessageProcessor.MISSING_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoSupportedMessageType() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, "invalid message type");
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MessageProcessor.UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoCaseListingID() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, MessageType.DELETE_HEARING);
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MessageProcessor.MISSING_CASE_LISTING_ID);
    }

    @Test
    void shouldProcessPendingRequest() {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(false);
        when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(true);

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService).markRequestWithGivenStatus(pendingRequest.getId(), "PROCESSING");
        verify(futureHearingRepository).createHearingRequest(any());
        verify(pendingRequestService).markRequestWithGivenStatus(pendingRequest.getId(), "COMPLETED");
    }

    @ParameterizedTest
    @MethodSource("provideExceptions")
    void shouldThrowJsonProcessingExceptionWhileProcessPendingRequest(Exception exception) {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(false);
        when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(true);
        doThrow(exception).when(futureHearingRepository).createHearingRequest(any());

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService).markRequestWithGivenStatus(pendingRequest.getId(), "PROCESSING");
        verify(futureHearingRepository).createHearingRequest(any());
        verify(pendingRequestService).markRequestAsPending(eq(pendingRequest.getId()),
                                                           eq(pendingRequest.getRetryCount()),
                                                           any());
    }

    private static Stream<Arguments> provideExceptions() {
        return Stream.of(
            Arguments.of(new JsonProcessingRuntimeException(new JsonProcessingException("N/A") {})),
            Arguments.of(new MalformedMessageException("N/A")),
            Arguments.of(new BadFutureHearingRequestException("N/A", null)),
            Arguments.of(new AuthenticationException("N/A")),
            Arguments.of(new ResourceNotFoundException("N/A"))
        );
    }

    private static Stream<Arguments> provideMessageTypes() {
        return Stream.of(
            Arguments.of(MessageType.REQUEST_HEARING.name(),
                         (Runnable) () -> verify(futureHearingRepository).createHearingRequest(any())),
            Arguments.of(MessageType.AMEND_HEARING.name(),
                         (Runnable) () -> verify(futureHearingRepository).amendHearingRequest(any(), any())),
            Arguments.of(MessageType.DELETE_HEARING.name(),
                         (Runnable) () -> verify(futureHearingRepository).deleteHearingRequest(any(), any()))
        );
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        pendingRequest.setMessageType("REQUEST_HEARING");
        pendingRequest.setMessage("{\"test\": \"name\"}");
        pendingRequest.setRetryCount(0);
        return pendingRequest;
    }

}

package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {

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
    private ServiceBusReceivedMessageContext messageContext;

    @Mock
    private ServiceBusReceivedMessage message;

    @Mock
    private ServiceBusMessageErrorHandler errorHandler;

    private MessageProcessor messageProcessor;

    private JsonNode anyData;

    @BeforeEach
     void setUp() {
        messageProcessor = new MessageProcessor(
                futureHearingRepository, errorHandler,
                messageSenderConfiguration,
                objectMapper,
                pendingRequestService);

        anyData = objectMapper.convertValue("test data", JsonNode.class);
    }

    @Test
    void shouldThrowErrorWhenCannotConvertMessage() throws JsonProcessingException {
        Map<String, Object> applicationProperties = Map.of(
            HEARING_ID, 1234567890,
            MESSAGE_TYPE, MessageType.REQUEST_HEARING.name()
        );
        when(message.getApplicationProperties()).thenReturn(applicationProperties);

        String messageBody = "invalid data";
        when(message.getBody()).thenReturn(BinaryData.fromString(messageBody));

        JsonParseException jsonParseException = new JsonParseException("json parse exception");
        when(objectMapper.readTree(messageBody)).thenThrow(jsonParseException);

        when(messageContext.getMessage()).thenReturn(message);

        messageProcessor.processMessage(messageContext);

        verify(messageContext, times(2)).getMessage();
        verify(message).getBody();
        verify(objectMapper).readTree(messageBody);
        verify(message).getApplicationProperties();
        verify(errorHandler).handleJsonError(messageContext, jsonParseException);
    }

    @ParameterizedTest
    @MethodSource("provideMessageTypes")
    void shouldInitiateHearing(String messageType, Runnable verifyMethod) {
        Map<String, Object> applicationProperties = Map.of(
            HEARING_ID, "1234567890",
            MESSAGE_TYPE, messageType
        );
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
        when(pendingRequestService.claimRequest(pendingRequest.getId())).thenReturn(1);

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService).claimRequest(pendingRequest.getId());
        verify(futureHearingRepository).createHearingRequest(any(), any());
        verify(pendingRequestService).markRequestWithGivenStatus(pendingRequest.getId(), "COMPLETED");
    }

    @ParameterizedTest
    @MethodSource("providePendingRequestTestCases")
    void shouldNotProcessPendingRequest(PendingRequestEntity pendingRequest, boolean submittedElapsed,
                                        boolean lastTriedElapsed) {
        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(submittedElapsed);
        // Strict mocking is enabled, so only mock lastTriedDateTimePeriodElapsed() if submittedElapsed is false.
        // If submittedElapsed is true, the short-circuiting logical operators in processPendingRequest() will prevent
        // lastTriedDateTimePeriodElapsed() from being called causing a test failure due to unnecessary mocking.
        if (!submittedElapsed) {
            when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(lastTriedElapsed);
        }

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService, never()).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService, never()).claimRequest(pendingRequest.getId());
        verify(futureHearingRepository, never()).createHearingRequest(any(), any());
        verify(pendingRequestService, never()).markRequestWithGivenStatus(pendingRequest.getId(), "COMPLETED");
    }

    private static Stream<Arguments> providePendingRequestTestCases() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        return Stream.of(
            Arguments.of(pendingRequest, true, true),  // Both time periods true
            Arguments.of(pendingRequest, true, false), // Only submitted elapsed is true
            Arguments.of(pendingRequest, false, false) // Both time periods false
        );
    }

    @ParameterizedTest
    @MethodSource("provideNonRetryableExceptions")
    void shouldThrowNonRetryableExceptionWhileProcessPendingRequest(Exception exception) {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(false);
        when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(true);
        when(pendingRequestService.claimRequest(pendingRequest.getId())).thenReturn(1);
        doThrow(exception).when(futureHearingRepository).createHearingRequest(any(), any());

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService).claimRequest(pendingRequest.getId());
        verify(futureHearingRepository).createHearingRequest(any(), any());
        verify(pendingRequestService).handleNonRetriableException(pendingRequest, exception);
    }

    @ParameterizedTest
    @MethodSource("provideRetryableExceptions")
    void shouldThrowRetryableExceptionWhileProcessPendingRequest(Exception exception) {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(false);
        when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(true);
        when(pendingRequestService.claimRequest(pendingRequest.getId())).thenReturn(1);
        doThrow(exception).when(futureHearingRepository).createHearingRequest(any(), any());

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService).claimRequest(pendingRequest.getId());
        verify(futureHearingRepository).createHearingRequest(any(), any());
        verify(pendingRequestService).markRequestAsPending(eq(pendingRequest.getId()),
                                                           eq(pendingRequest.getRetryCount()),
                                                           any());
    }

    @Test
    void shouldProcessPendingRequestNotInPendingState() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setStatus("PROCESSING");

        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(false);
        when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(true);
        when(pendingRequestService.claimRequest(pendingRequest.getId())).thenReturn(1);

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService, never())
            .markRequestAsPending(eq(pendingRequest.getId()), eq(pendingRequest.getRetryCount()), any());
        verify(pendingRequestService).markRequestWithGivenStatus(pendingRequest.getId(), "COMPLETED");
    }

    @Test
    void shouldNotProcessClaimedRequest() {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        when(pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)).thenReturn(false);
        when(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).thenReturn(true);
        when(pendingRequestService.claimRequest(pendingRequest.getId())).thenReturn(0);

        messageProcessor.processPendingRequest(pendingRequest);

        verify(pendingRequestService).findAndLockByHearingId(pendingRequest.getHearingId());
        verify(pendingRequestService).claimRequest(pendingRequest.getId());
        verify(futureHearingRepository, never()).createHearingRequest(any(), any());
        verify(pendingRequestService, never()).markRequestWithGivenStatus(pendingRequest.getId(), "COMPLETED");
    }

    private static Stream<Arguments> provideRetryableExceptions() {
        return Stream.of(
            Arguments.of(new JsonProcessingRuntimeException(new JsonProcessingException("N/A") {})),
            Arguments.of(new MalformedMessageException("N/A"))
        );
    }

    private static Stream<Arguments> provideNonRetryableExceptions() {
        return Stream.of(
            Arguments.of(new BadFutureHearingRequestException("N/A", null)),
            Arguments.of(new AuthenticationException("N/A", null)),
            Arguments.of(new ResourceNotFoundException("N/A , null"))
        );
    }

    private static Stream<Arguments> provideMessageTypes() {
        return Stream.of(
            Arguments.of(MessageType.REQUEST_HEARING.name(),
                         (Runnable) () -> verify(futureHearingRepository).createHearingRequest(any(), any())),
            Arguments.of(MessageType.AMEND_HEARING.name(),
                         (Runnable) () -> verify(futureHearingRepository).amendHearingRequest(any(), any())),
            Arguments.of(MessageType.DELETE_HEARING.name(),
                         (Runnable) () -> verify(futureHearingRepository).deleteHearingRequest(any(), any()))
        );
    }

    private static PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        pendingRequest.setMessageType("REQUEST_HEARING");
        pendingRequest.setMessage("{\"test\": \"name\"}");
        pendingRequest.setRetryCount(0);
        pendingRequest.setStatus(PendingStatusType.PENDING.name());
        return pendingRequest;
    }

}

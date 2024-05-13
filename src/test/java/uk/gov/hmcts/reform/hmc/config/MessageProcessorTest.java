package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;
import uk.gov.hmcts.reform.hmc.service.MessageProcessor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

class MessageProcessorTest {

    private MessageProcessor messageProcessor;
    private static final String HEARING_ID = "hearing_id";
    private static final String MESSAGE_TYPE = "message_type";
    public static final String MISSING_CASE_LISTING_ID = "Message is missing custom header hearing_id";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "Message has unsupported value for message_type";
    public static final String MISSING_MESSAGE_TYPE = "Message is missing custom header message_type";

    @Mock
    private DefaultFutureHearingRepository futureHearingRepository;

    @Mock
    private PendingRequestRepository pendingRequestRepository;

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
                objectMapper);
        anyData = objectMapper.convertValue("test data", JsonNode.class);
        String requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(new AuthenticationResponse());
    }

    @Test
    void shouldInitiateRequestHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(HEARING_ID, "1234567890");
        applicationProperties.put(MESSAGE_TYPE, MessageType.REQUEST_HEARING);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        try {
            messageProcessor.processMessage(anyData, applicationProperties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        verify(futureHearingRepository).createHearingRequest(any());
    }

    @Test
    void shouldInitiateAmendHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(HEARING_ID, "1234567890");
        applicationProperties.put(MESSAGE_TYPE, MessageType.AMEND_HEARING);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        try {
            messageProcessor.processMessage(anyData, applicationProperties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        verify(futureHearingRepository).amendHearingRequest(any(), any());
    }

    @Test
    void shouldInitiateDeleteHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(HEARING_ID, "1234567890");
        applicationProperties.put(MESSAGE_TYPE, MessageType.DELETE_HEARING);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        try {
            messageProcessor.processMessage(anyData, applicationProperties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        verify(futureHearingRepository).deleteHearingRequest(any(), any());
    }

    @Test
    void shouldThrowErrorWhenMessageTypeIsNull() {
        Map<String, Object> applicationProperties = new HashMap<>();
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MISSING_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenCannotConvertMessage() {
        Map<String, Object> applicationProperties = new HashMap<>();
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("invalid data"));
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
                .isInstanceOf(MalformedMessageException.class)
                .hasMessageContaining(MISSING_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoMessageType() {
        Map<String, Object> applicationProperties = new HashMap<>();
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MISSING_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoSupportedMessageType() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, "invalid message type");
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoCaseListingID() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, MessageType.DELETE_HEARING);
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MISSING_CASE_LISTING_ID);
    }
}

package uk.gov.hmcts.reform.hmc.config;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private String requestString;
    private MessageProcessor messageProcessor;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Mock
    private HearingManagementInterfaceApiClient hmiClient;

    @Mock
    private DefaultFutureHearingRepository futureHearingRepository;

    @Mock
    private ServiceBusReceiverClient client;

    @Mock
    private ServiceBusReceivedMessage message;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        messageProcessor = new MessageProcessor(futureHearingRepository);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(new AuthenticationResponse());

    }

    @Test
    void shouldInitiateRequestHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("hearing_id", "1234567890");
        applicationProperties.put("message_type", MessageType.REQUEST_HEARING);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        messageProcessor.processMessage(client, message);
        verify(futureHearingRepository).createHearingRequest(any());
    }

    @Test
    void shouldInitiateAmendHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("hearing_id", "1234567890");
        applicationProperties.put("message_type", MessageType.AMEND_HEARING);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        messageProcessor.processMessage(client, message);
        verify(futureHearingRepository).amendHearingRequest(any(), any());
    }

    @Test
    void shouldInitiateDeleteHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("hearing_id", "1234567890");
        applicationProperties.put("message_type", MessageType.DELETE_HEARING);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        when(message.getBody()).thenReturn(BinaryData.fromString("{ \"test\": \"name\"}"));
        messageProcessor.processMessage(client, message);
        verify(futureHearingRepository).deleteHearingRequest(any(), any());
    }

    @Test
    void shouldProcessWhenMessageTypeIsNull() {
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, null, null);
    }
}

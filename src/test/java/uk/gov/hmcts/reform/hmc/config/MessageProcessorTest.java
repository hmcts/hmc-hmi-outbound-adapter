package uk.gov.hmcts.reform.hmc.config;

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

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        messageProcessor = new MessageProcessor(applicationParams,
                                                activeDirectoryApiClient,
                                                hmiClient,
                                                futureHearingRepository);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(new AuthenticationResponse());

    }

    @Test
    void shouldInitiateRequestHearing() {
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, MessageType.REQUEST_HEARING, null);
        verify(futureHearingRepository).createHearingRequest(any());
    }

    @Test
    void shouldInitiateAmendHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("hearing_id", "1234567890");
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, MessageType.AMEND_HEARING, applicationProperties);
        verify(futureHearingRepository).amendHearingRequest(any(), any());
    }

    @Test
    void shouldInitiateDeleteHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put("hearing_id", "1234567890");
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, MessageType.DELETE_HEARING, applicationProperties);
        verify(futureHearingRepository).deleteHearingRequest(any(), any());
    }

    @Test
    void shouldProcessWhenMessageTypeIsNull() {
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, null, null);
    }
}

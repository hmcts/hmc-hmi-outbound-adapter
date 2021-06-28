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
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.hmc.config.MessageProcessor.MISSING_CASE_LISTING_ID;
import static uk.gov.hmcts.reform.hmc.config.MessageProcessor.MISSING_MESSAGE_TYPE;
import static uk.gov.hmcts.reform.hmc.config.MessageProcessor.UNSUPPORTED_MESSAGE_TYPE;

class MessageProcessorTest {
    private static final String MESSAGE_TYPE = "message_type";
    private static final String CASE_LISTING_ID = "caseListingID";

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

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        messageProcessor = new MessageProcessor(applicationParams, activeDirectoryApiClient, hmiClient);
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
        applicationProperties.put(MESSAGE_TYPE, MessageType.REQUEST_HEARING);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, applicationProperties);
    }

    @Test
    void shouldInitiateAmendHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, MessageType.AMEND_HEARING);
        applicationProperties.put(CASE_LISTING_ID, "1234567890");
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, applicationProperties);
    }

    @Test
    void shouldInitiateDeleteHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, MessageType.DELETE_HEARING);
        applicationProperties.put(CASE_LISTING_ID, "1234567890");
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        messageProcessor.processMessage(anyData, applicationProperties);
    }

    @Test
    void shouldThrowErrorWhenNoMessageType() {
        Map<String, Object> applicationProperties = new HashMap<>();
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MISSING_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoSupportedMessageType() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, "invalid message type");
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    void shouldThrowErrorWhenNoCaseListingID() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, MessageType.DELETE_HEARING);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        assertThatThrownBy(() -> messageProcessor.processMessage(anyData, applicationProperties))
            .isInstanceOf(MalformedMessageException.class)
            .hasMessageContaining(MISSING_CASE_LISTING_ID);
    }
}

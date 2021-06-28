package uk.gov.hmcts.reform.hmc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.HashMap;
import java.util.Map;

import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyAmendHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyDeleteHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyRequestHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyReturnToken;

class MessageProcessorIT extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private static final JsonNode data = OBJECT_MAPPER.convertValue("Test data", JsonNode.class);
    private static final String TOKEN = "example-token";
    private static final String CASE_LISTING_REQUEST_ID = "testCaseListingRequestId";
    private static final String MESSAGE_TYPE = "message_type";
    private static final String CASE_LISTING_ID = "caseListingID";

    @MockBean
    private MessageReceiverConfiguration messageReceiverConfiguration;

    @Autowired
    private ApplicationParams applicationParams;

    @Autowired
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Autowired
    private HearingManagementInterfaceApiClient hmiClient;

    @Autowired
    private DefaultFutureHearingRepository defaultFutureHearingRepository;

    @Test
    void shouldInitiateRequestHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MESSAGE_TYPE, MessageType.REQUEST_HEARING);
        stubSuccessfullyReturnToken(TOKEN);
        stubSuccessfullyRequestHearing(TOKEN);

        MessageProcessor messageProcessor = new MessageProcessor(applicationParams,
                                                                 activeDirectoryApiClient, hmiClient);
        messageProcessor.processMessage(data, applicationProperties);
    }

    @Test
    void shouldInitiateDeleteHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(CASE_LISTING_ID, CASE_LISTING_REQUEST_ID);
        applicationProperties.put(MESSAGE_TYPE, MessageType.DELETE_HEARING);
        stubSuccessfullyReturnToken(TOKEN);
        stubSuccessfullyDeleteHearing(TOKEN, CASE_LISTING_REQUEST_ID);

        MessageProcessor messageProcessor = new MessageProcessor(applicationParams,
                                                                 activeDirectoryApiClient, hmiClient);
        messageProcessor.processMessage(data, applicationProperties);
    }

    @Test
    void shouldInitiateAmendHearing() {
        Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(CASE_LISTING_ID, CASE_LISTING_REQUEST_ID);
        applicationProperties.put(MESSAGE_TYPE, MessageType.AMEND_HEARING);
        stubSuccessfullyReturnToken(TOKEN);
        stubSuccessfullyAmendHearing(TOKEN, CASE_LISTING_REQUEST_ID);

        MessageProcessor messageProcessor = new MessageProcessor(applicationParams,
                                                                 activeDirectoryApiClient, hmiClient);
        messageProcessor.processMessage(data, applicationProperties);
    }

}

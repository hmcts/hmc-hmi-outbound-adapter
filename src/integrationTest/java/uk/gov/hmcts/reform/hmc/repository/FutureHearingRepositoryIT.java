package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingRequestPayload;
import uk.gov.hmcts.reform.hmc.client.futurehearing.model.CaseDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.model.HearingRequest;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubPostMethodThrowingAuthenticationError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubPutMethodThrowingAuthenticationError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyAmendHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyRequestHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyReturnToken;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.REQUEST_NOT_FOUND;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;
import static uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository.REQUEST_ID_NOT_FOUND;

public class FutureHearingRepositoryIT extends BaseTest {

    private static final String TOKEN = "example-token";
    private static final String GET_TOKEN_URL = "/FH_GET_TOKEN_URL";
    private static final String HMI_REQUEST_URL = "/hearings";
    private static final String TEST_ID = "test-id";
    private static final String HMI_REQUEST_URL_WITH_ID = "/hearings/" + TEST_ID;
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private static final JsonNode data = OBJECT_MAPPER.convertValue("Test data", JsonNode.class);


    @Autowired
    private ApplicationParams applicationParams;

    @Autowired
    private DefaultFutureHearingRepository defaultFutureHearingRepository;

    @Nested
    @DisplayName("Retrieve Authorisation Token")
    class RetrieveAuthorisationToken {

        @Test
        public void shouldSuccessfullyReturnAuthenticationObject() {
            stubSuccessfullyReturnToken(TOKEN);
            AuthenticationResponse response = defaultFutureHearingRepository.retrieveAuthToken();
            assertEquals(response.getAccessToken(), TOKEN);
        }

        @Test
        public void shouldThrow400AuthenticationException() {
            stubPostMethodThrowingAuthenticationError(400, GET_TOKEN_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.retrieveAuthToken())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        public void shouldThrow401AuthenticationException() {
            stubPostMethodThrowingAuthenticationError(401, GET_TOKEN_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.retrieveAuthToken())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        public void shouldThrow500AuthenticationException() {
            stubPostMethodThrowingAuthenticationError(500, GET_TOKEN_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.retrieveAuthToken())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("Create Hearing Request")
    class CreateHearingRequest {

        @Test
        public void shouldSuccessfullyRequestAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyRequestHearing(TOKEN);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository.createHearingRequest(data);
            assertEquals(response.getResponseCode(), 202);
        }

        @Test
        public void shouldThrow400AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPostMethodThrowingAuthenticationError(400, HMI_REQUEST_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.createHearingRequest(data))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        public void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPostMethodThrowingAuthenticationError(401, HMI_REQUEST_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.createHearingRequest(data))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        public void shouldThrow500AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPostMethodThrowingAuthenticationError(500, HMI_REQUEST_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.createHearingRequest(data))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(SERVER_ERROR);
        }

    }

    @Nested
    @DisplayName("Create Hearing Request")
    class AmendHearingRequest {

        private JsonNode viableData;

        @BeforeEach
        public void setUp() {
            CaseDetails details = new CaseDetails();
            details.setCaseListingRequestId(TEST_ID);
            HearingRequest request = new HearingRequest();
            request.setCaseDetails(details);
            HearingRequestPayload payload = new HearingRequestPayload();
            payload.setHearingRequest(request);
            viableData = OBJECT_MAPPER.convertValue(payload, JsonNode.class);
        }

        @Test
        public void shouldSuccessfullyAmendAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyAmendHearing(TOKEN, TEST_ID);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository
                .amendHearingRequest(viableData);
            assertEquals(response.getResponseCode(), 202);
        }

        @Test
        public void shouldThrow400AuthenticationExceptionWhenNoCaseListingRequestIdFound() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingAuthenticationError(400, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(REQUEST_ID_NOT_FOUND);
        }

        @Test
        public void shouldThrow400AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingAuthenticationError(400, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(viableData))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        public void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingAuthenticationError(401, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(viableData))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        public void shouldThrow404AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingAuthenticationError(404, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(viableData))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(REQUEST_NOT_FOUND);
        }
    }
}


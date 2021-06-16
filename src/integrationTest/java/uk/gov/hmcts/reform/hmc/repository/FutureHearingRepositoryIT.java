package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubDeleteMethodThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubPostMethodThrowingAuthenticationError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubPutMethodThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyAmendHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyDeleteHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyRequestHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyReturnToken;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.REQUEST_NOT_FOUND;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;

public class FutureHearingRepositoryIT extends BaseTest {

    private static final String TOKEN = "example-token";
    private static final String GET_TOKEN_URL = "/FH_GET_TOKEN_URL";
    private static final String HMI_REQUEST_URL = "/hearings";
    private static final String CASE_LISTING_REQUEST_ID = "testCaseListingRequestId";
    private static final String HMI_REQUEST_URL_WITH_ID = "/hearings/" + CASE_LISTING_REQUEST_ID;
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
    @DisplayName("Amend Hearing Request")
    class AmendHearingRequest {

        @Test
        public void shouldSuccessfullyAmendAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyAmendHearing(TOKEN, CASE_LISTING_REQUEST_ID);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository
                .amendHearingRequest(data, CASE_LISTING_REQUEST_ID);
            assertEquals(response.getResponseCode(), 202);
        }

        @Test
        public void shouldThrow400AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingError(400, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        public void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingError(401, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        public void shouldThrow404ResourceNotFoundException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingError(404, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(REQUEST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Delete Hearing Request")
    class DeleteHearingRequest {

        @Test
        public void shouldSuccessfullyDeleteAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyDeleteHearing(TOKEN, CASE_LISTING_REQUEST_ID);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository
                .deleteHearingRequest(data, CASE_LISTING_REQUEST_ID);
            assertEquals(response.getResponseCode(), 200);
        }

        @Test
        public void shouldThrow400AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubDeleteMethodThrowingError(400, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        public void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubDeleteMethodThrowingError(401, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }
    }
}


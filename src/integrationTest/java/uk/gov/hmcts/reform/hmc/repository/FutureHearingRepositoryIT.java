package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.domain.Sort;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HealthCheckResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.config.MessageReceiverConfiguration;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.ApiClientException;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckActiveDirectoryException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckHmiException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubDeleteMethodThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnToken;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnTokenHtmlResponse;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheck;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheckThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheckThrowingErrorHtmlResponse;
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
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_AUTH_REQUEST;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI_TO_HMC_AUTH_FAIL;

@ExtendWith(SpringExtension.class)
public class FutureHearingRepositoryIT extends BaseTest {

    private static final String TOKEN = "example-token";
    private static final String GET_TOKEN_URL = "/FH_GET_TOKEN_URL";
    private static final String HMI_REQUEST_URL = "/hearings";
    private static final String CASE_LISTING_REQUEST_ID = "2000000000";
    private static final String HMI_REQUEST_URL_WITH_ID = "/hearings/" + CASE_LISTING_REQUEST_ID;
    private static final String HTML_INTERNAL_SERVER_ERROR =
        "<html><head><title>500 Internal Server Error</title></head></html>";
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private static final JsonNode data = OBJECT_MAPPER.convertValue("Test data", JsonNode.class);
    private static final String DELETE_HEARING_DATA_SCRIPT = "classpath:sql/delete-hearing-tables.sql";
    private static final String INSERT_HEARINGS_DATA_SCRIPT = "classpath:sql/insert-case_hearing_request.sql";

    @MockitoBean
    private MessageReceiverConfiguration messageReceiverConfiguration;

    private final DefaultFutureHearingRepository defaultFutureHearingRepository;

    private final HearingStatusAuditRepository hearingStatusAuditRepository;

    @Autowired
    public FutureHearingRepositoryIT(DefaultFutureHearingRepository defaultFutureHearingRepository,
                                     HearingStatusAuditRepository hearingStatusAuditRepository) {
        this.defaultFutureHearingRepository = defaultFutureHearingRepository;
        this.hearingStatusAuditRepository = hearingStatusAuditRepository;
    }

    @Nested
    @DisplayName("Retrieve Authorisation Token")
    class RetrieveAuthorisationToken {

        @Test
        void shouldSuccessfullyReturnAuthenticationObject() {
            stubSuccessfullyReturnToken(TOKEN);
            AuthenticationResponse response = defaultFutureHearingRepository.retrieveAuthToken();
            assertEquals(TOKEN, response.getAccessToken());
        }

        @Test
        void shouldThrow400BadFutureHearingRequestException() {
            stubPostMethodThrowingAuthenticationError(400, GET_TOKEN_URL);
            assertThatThrownBy(defaultFutureHearingRepository::retrieveAuthToken)
                .isInstanceOf(BadFutureHearingRequestException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        void shouldThrow401AuthenticationException() {
            stubPostMethodThrowingAuthenticationError(401, GET_TOKEN_URL);
            assertThatThrownBy(defaultFutureHearingRepository::retrieveAuthToken)
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        void shouldThrow500AuthenticationException() {
            stubPostMethodThrowingAuthenticationError(500, GET_TOKEN_URL);
            assertThatThrownBy(defaultFutureHearingRepository::retrieveAuthToken)
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("HMI API Private Health Check")
    class PrivateHealthCheck {

        @ParameterizedTest
        @MethodSource("uk.gov.hmcts.reform.hmc.utils.TestingUtil#healthStatuses")
        void shouldSuccessfullyGetHealth(Status healthStatus) {
            stubSuccessfullyReturnToken(TOKEN);
            stubHealthCheck(TOKEN, healthStatus);

            HealthCheckResponse response = defaultFutureHearingRepository.privateHealthCheck();

            assertEquals(healthStatus, response.getStatus(), "Health check response has unexpected health status");
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("uk.gov.hmcts.reform.hmc.utils.TestingUtil#adApiErrorsAndExpectedHealthCheckValues")
        void shouldThrowHealthCheckActiveDirectoryExceptionForActiveDirectoryErrors(int status,
                                                                                    String errorDescription,
                                                                                    List<Integer> errorCodes,
                                                                                    String expectedApiName,
                                                                                    String expectedExceptionMessage,
                                                                                    Integer expectedErrorCode,
                                                                                    String expectedErrorDescription) {
            stubFailToReturnToken(status, errorDescription, errorCodes);

            HealthCheckActiveDirectoryException exception =
                assertThrows(HealthCheckActiveDirectoryException.class,
                             defaultFutureHearingRepository::privateHealthCheck,
                             "HealthCheckActiveDirectoryException should be thrown");

            assertHealthCheckException(exception,
                                       expectedApiName,
                                       expectedExceptionMessage,
                                       expectedErrorCode,
                                       expectedErrorDescription);
        }

        @Test
        void shouldThrowHealthCheckActiveDirectoryExceptionForActiveDirectoryApiClientException() {
            stubFailToReturnTokenHtmlResponse(500, HTML_INTERNAL_SERVER_ERROR);

            HealthCheckActiveDirectoryException exception =
                assertThrows(HealthCheckActiveDirectoryException.class,
                             defaultFutureHearingRepository::privateHealthCheck,
                             "HealthCheckActiveDirectoryException should be thrown");

            assertHealthCheckException(exception, "ActiveDirectory", SERVER_ERROR, 500, HTML_INTERNAL_SERVER_ERROR);
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("uk.gov.hmcts.reform.hmc.utils.TestingUtil#hmiApiErrorsAndExpectedHealthCheckValues")
        void shouldThrowHealthCheckHmiExceptionForHmiErrors(int errorStatus,
                                                            String errorMessage,
                                                            String expectedApiName,
                                                            String expectedExceptionMessage,
                                                            Integer expectedErrorCode,
                                                            String expectedErrorDescription) {
            stubSuccessfullyReturnToken(TOKEN);
            stubHealthCheckThrowingError(errorStatus, errorMessage);

            HealthCheckHmiException exception =
                assertThrows(HealthCheckHmiException.class,
                             defaultFutureHearingRepository::privateHealthCheck,
                             "HealthCheckHmiException should be thrown");

            assertHealthCheckException(exception,
                                       expectedApiName,
                                       expectedExceptionMessage,
                                       expectedErrorCode,
                                       expectedErrorDescription);
        }

        @Test
        void shouldThrowHealthCheckHmiExceptionForHmiApiClientException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubHealthCheckThrowingErrorHtmlResponse(500, HTML_INTERNAL_SERVER_ERROR);

            HealthCheckHmiException exception =
                assertThrows(HealthCheckHmiException.class,
                             defaultFutureHearingRepository::privateHealthCheck,
                             "HealthCheckHmiException should be thrown");

            assertHealthCheckException(exception, "HearingManagementInterface", SERVER_ERROR, 500,
                                       HTML_INTERNAL_SERVER_ERROR
            );
        }

        private void assertHealthCheckException(HealthCheckException healthCheckException,
                                                String expectedApiName,
                                                String expectedExceptionMessage,
                                                Integer expectedErrorCode,
                                                String expectedErrorDescription) {
            assertEquals(expectedApiName,
                         healthCheckException.getApiName(),
                         "Health Check exception has unexpected API name");

            assertEquals(expectedExceptionMessage,
                         healthCheckException.getMessage(),
                         "Health Check exception has unexpected message");

            if (expectedErrorCode == null) {
                assertNull(healthCheckException.getErrorCode(), "Health Check exception errorCode should be null");
            } else {
                assertEquals(expectedErrorCode,
                             healthCheckException.getErrorCode(),
                             "Health Check exception has unexpected error code");
            }

            if (expectedErrorDescription == null) {
                assertNull(healthCheckException.getErrorDescription(),
                           "Health Check exception errorDescription should be null");
            } else {
                assertEquals(expectedErrorDescription,
                             healthCheckException.getErrorDescription(),
                             "Health Check exception has unexpected error description");
            }
        }
    }

    @Nested
    @DisplayName("Create Hearing Request")
    @Sql(scripts = {DELETE_HEARING_DATA_SCRIPT, INSERT_HEARINGS_DATA_SCRIPT})
    class CreateHearingRequest {

        @Test
        void shouldSuccessfullyRequestAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyRequestHearing(TOKEN);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository.createHearingRequest(data,
                                                                              CASE_LISTING_REQUEST_ID);
            assertEquals(202, response.getResponseCode());
        }

        @Test
        void shouldThrow400BadFutureHearingRequestException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPostMethodThrowingAuthenticationError(400, HMI_REQUEST_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.createHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(BadFutureHearingRequestException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPostMethodThrowingAuthenticationError(401, HMI_REQUEST_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.createHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        void shouldThrow500AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPostMethodThrowingAuthenticationError(500, HMI_REQUEST_URL);
            assertThatThrownBy(() -> defaultFutureHearingRepository.createHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("Amend Hearing Request")
    @Sql(scripts = {DELETE_HEARING_DATA_SCRIPT, INSERT_HEARINGS_DATA_SCRIPT})
    class AmendHearingRequest {

        @Test
        void shouldSuccessfullyAmendAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyAmendHearing(TOKEN, CASE_LISTING_REQUEST_ID);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository
                .amendHearingRequest(data, CASE_LISTING_REQUEST_ID);
            assertEquals(202, response.getResponseCode());
        }

        @Test
        void shouldThrow400BadFutureHearingRequestException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingError(400, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(BadFutureHearingRequestException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingError(401, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        void shouldThrow404ResourceNotFoundException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubPutMethodThrowingError(404, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.amendHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(REQUEST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Delete Hearing Request")
    @Sql(scripts = {DELETE_HEARING_DATA_SCRIPT, INSERT_HEARINGS_DATA_SCRIPT})
    class DeleteHearingRequest {

        @Test
        void shouldSuccessfullyDeleteAHearing() {
            stubSuccessfullyReturnToken(TOKEN);
            stubSuccessfullyDeleteHearing(TOKEN, CASE_LISTING_REQUEST_ID);
            HearingManagementInterfaceResponse response = defaultFutureHearingRepository
                .deleteHearingRequest(data, CASE_LISTING_REQUEST_ID);
            assertEquals(200, response.getResponseCode());
        }

        @Test
        void shouldThrow400BadFutureHearingRequestException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubDeleteMethodThrowingError(400, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(BadFutureHearingRequestException.class)
                .hasMessageContaining(INVALID_REQUEST);
        }

        @Test
        void shouldThrow401AuthenticationException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubDeleteMethodThrowingError(401, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);
        }

        @Test
        void shouldThrow404ResourceNotFoundException() {
            stubSuccessfullyReturnToken(TOKEN);
            stubDeleteMethodThrowingError(404, HMI_REQUEST_URL_WITH_ID);
            assertThatThrownBy(() -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(REQUEST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Authorisation Token Request Failure")
    @Sql(scripts = {DELETE_HEARING_DATA_SCRIPT, INSERT_HEARINGS_DATA_SCRIPT})
    class AuthorisationTokenRequestFailure {

        @Test
        void shouldRethrowBadFutureHearingRequestExceptionWhen400Error() {
            stubFailToReturnToken(400, "1000: Failed to get token", List.of(1000));

            BadFutureHearingRequestException exception =
                assertThrows(BadFutureHearingRequestException.class,
                             () -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID),
                             "BadFutureHearingRequestException should be thrown");
            assertEquals(INVALID_REQUEST, exception.getMessage());

            ErrorDetails errorDetails = exception.getErrorDetails();
            assertErrorDetails(errorDetails, "1000: Failed to get token", 1000);

            assertHearingStatusAuditEntities(INVALID_REQUEST);
        }

        @Test
        void shouldRethrowAuthenticationExceptionWhen401Error() {
            stubFailToReturnToken(401, "2000: Failed to get token", List.of(2000));

            AuthenticationException exception =
                assertThrows(AuthenticationException.class,
                             () -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID),
                             "AuthenticationException should be thrown");

            ErrorDetails errorDetails = exception.getErrorDetails();
            assertErrorDetails(errorDetails, "2000: Failed to get token", 2000);

            assertHearingStatusAuditEntities(INVALID_SECRET);
        }

        @Test
        void shouldRethrowResourceNotFoundExceptionWhen404Error() {
            stubFailToReturnToken(404, "3000: Failed to get token", List.of(3000));

            ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class,
                             () -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID),
                             "ResourceNotFoundException should be thrown");

            assertEquals(REQUEST_NOT_FOUND, exception.getMessage());
            assertHearingStatusAuditEntities(REQUEST_NOT_FOUND);
        }

        @Test
        void shouldRethrowAuthenticationExceptionWhen405Error() {
            stubFailToReturnToken(405, "4000: Failed to get token", List.of(4000));

            AuthenticationException exception =
                assertThrows(AuthenticationException.class,
                             () -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID),
                             "AuthenticationException should be thrown");

            ErrorDetails errorDetails = exception.getErrorDetails();
            assertErrorDetails(errorDetails, "4000: Failed to get token", 4000);

            assertHearingStatusAuditEntities(SERVER_ERROR);
        }

        @Test
        void shouldRethrowApiClientExceptionWhen500ErrorWithNonJsonBody() {
            stubFailToReturnTokenHtmlResponse(500, HTML_INTERNAL_SERVER_ERROR);

            ApiClientException exception =
                assertThrows(ApiClientException.class,
                             () -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID),
                             "ApiClientException should be thrown");

            assertEquals(500, exception.getErrorCode());
            assertEquals(HTML_INTERNAL_SERVER_ERROR, exception.getErrorDescription());

            assertHearingStatusAuditEntities(SERVER_ERROR);
        }

        private void assertErrorDetails(ErrorDetails errorDetails,
                                        String expectedAuthErrorDescription,
                                        Integer expectedAuthErrorCode) {
            assertNotNull(errorDetails, "Error details should not be null");
            assertEquals(expectedAuthErrorDescription,
                         errorDetails.getAuthErrorDescription(),
                         "Error details auth error description has unexpected value");

            List<Integer> authErrorCodes = errorDetails.getAuthErrorCodes();
            assertNotNull(authErrorCodes, "Error details auth error codes should not be null");
            assertEquals(1, authErrorCodes.size(), "Unexpected number of auth error codes");
            assertEquals(expectedAuthErrorCode, authErrorCodes.getFirst(), "Auth error code has unexpected value");
        }

        private void assertHearingStatusAuditEntities(String expectedErrorDescription) {
            List<HearingStatusAuditEntity> hearingAuditStatusList =
                hearingStatusAuditRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            assertNotNull(hearingAuditStatusList, "Hearing status audit records should be created");
            assertEquals(2, hearingAuditStatusList.size(), "Unexpected number of hearing status audit records");

            assertHearingStatusAuditEntity(hearingAuditStatusList.getFirst(), HMC_TO_HMI_AUTH_REQUEST, null);
            assertHearingStatusAuditEntity(hearingAuditStatusList.get(1),
                                           HMI_TO_HMC_AUTH_FAIL,
                                           expectedErrorDescription);
        }

        private void assertHearingStatusAuditEntity(HearingStatusAuditEntity hearingStatusAudit,
                                                    String expectedHearingEvent,
                                                    String expectedErrorDescription) {
            assertEquals(expectedHearingEvent,
                         hearingStatusAudit.getHearingEvent(),
                         "Hearing status audit has unexpected hearing event");

            if (expectedErrorDescription == null) {
                assertNull(hearingStatusAudit.getErrorDescription(),
                           "Hearing status audit error description should be null");
            } else {
                JsonNode errorDescription = hearingStatusAudit.getErrorDescription();
                assertNotNull(errorDescription, "Hearing status audit error description should not be null");
                assertEquals(expectedErrorDescription,
                             errorDescription.asText(),
                             "Hearing status audit has unexpected error description");
            }
        }
    }
}


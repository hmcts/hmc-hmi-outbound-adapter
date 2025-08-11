package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HealthCheckResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.config.MessageReceiverConfiguration;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckActiveDirectoryException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckHmiException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubDeleteMethodThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnToken;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheck;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheckThrowingError;
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
    private static final String CASE_LISTING_REQUEST_ID = "2000000000";
    private static final String HMI_REQUEST_URL_WITH_ID = "/hearings/" + CASE_LISTING_REQUEST_ID;
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private static final JsonNode data = OBJECT_MAPPER.convertValue("Test data", JsonNode.class);
    private static final String DELETE_HEARING_DATA_SCRIPT = "classpath:sql/delete-hearing-tables.sql";
    private static final String INSERT_HEARINGS_DATA_SCRIPT = "classpath:sql/insert-case_hearing_request.sql";

    @MockBean
    private MessageReceiverConfiguration messageReceiverConfiguration;

    private final DefaultFutureHearingRepository defaultFutureHearingRepository;

    @Autowired
    public FutureHearingRepositoryIT(DefaultFutureHearingRepository defaultFutureHearingRepository) {
        this.defaultFutureHearingRepository = defaultFutureHearingRepository;
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
        @MethodSource("healthCheckStatuses")
        void shouldSuccessfullyGetHealth(Status healthStatus) {
            stubSuccessfullyReturnToken(TOKEN);
            stubHealthCheck(TOKEN, healthStatus);

            HealthCheckResponse response = defaultFutureHearingRepository.privateHealthCheck();

            assertEquals(healthStatus, response.getStatus(), "Health check response has unexpected health status");
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("activeDirectoryErrors")
        void shouldThrowHealthCheckActiveDirectoryExceptionForActiveDirectoryErrors(int status,
                                                                                    String errorDescription,
                                                                                    List<Integer> errorCodes,
                                                                                    String expectedExceptionMessage,
                                                                                    Integer expectedErrorCode,
                                                                                    String expectedErrorDescription) {
            stubFailToReturnToken(status, errorDescription, errorCodes);

            HealthCheckActiveDirectoryException exception =
                assertThrows(HealthCheckActiveDirectoryException.class,
                             defaultFutureHearingRepository::privateHealthCheck,
                             "HealthCheckActiveDirectoryException should be thrown");

            assertHealthCheckException(exception,
                                       expectedExceptionMessage,
                                       "ActiveDirectory",
                                       expectedErrorCode,
                                       expectedErrorDescription);
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("hmiErrors")
        void shouldThrowHealthCheckHmiExceptionForHmiErrors(int errorStatus,
                                                            String errorMessage,
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
                                       expectedExceptionMessage,
                                       "HearingManagementInterface",
                                       expectedErrorCode,
                                       expectedErrorDescription);
        }

        private static Stream<Arguments> healthCheckStatuses() {
            return Stream.of(
                arguments(Status.UP),
                arguments(Status.DOWN),
                arguments(Status.UNKNOWN),
                arguments(Status.OUT_OF_SERVICE)
            );
        }

        private static Stream<Arguments> activeDirectoryErrors() {
            return Stream.of(
                arguments(named("400 - BadFutureHearingRequestException", 400),
                          "AADSTS1002012: The provided value for scope scope is not valid.",
                          List.of(1002012),
                          INVALID_REQUEST,
                          1002012,
                          "AADSTS1002012: The provided value for scope scope is not valid."),
                arguments(named("401 - AuthenticationException", 401),
                          "AADSTS7000215: Invalid client secret provided.",
                          List.of(7000215),
                          INVALID_SECRET,
                          7000215,
                          "AADSTS7000215: Invalid client secret provided."),
                arguments(named("404 - Resource not found", 404),
                          "ActiveDirectory resource not found",
                          List.of(1000, 2000),
                          "Resource not found",
                          null,
                          null),
                arguments(named("500 - AuthenticationException", 500),
                          "Internal server error",
                          List.of(3000, 4000),
                          SERVER_ERROR,
                          3000,
                          "Internal server error")
            );
        }

        private static Stream<Arguments> hmiErrors() {
            return Stream.of(
                arguments(named("400 - BadFutureHearingRequestException", 400),
                          "Missing/Invalid Header Source-System",
                          INVALID_REQUEST,
                          400,
                          "Missing/Invalid Header Source-System"),
                arguments(named("401 - AuthenticationException", 401),
                          "Access denied due to invalid OAuth information",
                          INVALID_SECRET,
                          401,
                          "Access denied due to invalid OAuth information"),
                arguments(named("404 - ResourceNotFoundException", 404),
                          "HMI Resource not found",
                          "Resource not found",
                          null,
                          null),
                arguments(named("500 - AuthenticationException", 500),
                          "Internal server error",
                          SERVER_ERROR,
                          500,
                          "Internal server error")
            );
        }

        private void assertHealthCheckException(HealthCheckException healthCheckException,
                                                String expectedExceptionMessage,
                                                String expectedApiName,
                                                Integer expectedErrorCode,
                                                String expectedErrorDescription) {
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

            assertEquals(expectedApiName,
                         healthCheckException.getApiName(),
                         "Health Check exception has unexpected API name");

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

        @Test
        void shouldThrowAuthExceptionWhenAuthTokenFails() {
            stubFailToReturnToken(401, "Failed to get token", List.of(1000));
            assertThatThrownBy(() -> defaultFutureHearingRepository.deleteHearingRequest(data, CASE_LISTING_REQUEST_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Failed to retrieve authorization token for operation: "
                                          + "deleteHearingRequest hearingId: 2000000000");
        }
    }
}


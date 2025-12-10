package uk.gov.hmcts.reform.hmc.repository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HealthCheckResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckActiveDirectoryException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckHmiException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditServiceImpl;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static feign.Request.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class FutureHearingRepositoryTest {

    private AuthenticationResponse response;
    private String requestString;
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();

    private static final String CASE_LISTING_REQUEST_ID = "2000000000";

    private static final String EXCEPTION_MESSAGE_BAD_REQUEST = "Bad request exception";
    private static final String ERROR_DESCRIPTION_BAD_REQUEST = "Bad request error";
    private static final String EXCEPTION_MESSAGE_AUTH = "Auth exception";
    private static final String ERROR_DESCRIPTION_AUTH = "Auth error";

    private static final Logger logger = (Logger) LoggerFactory.getLogger(DefaultFutureHearingRepository.class);

    @InjectMocks
    private DefaultFutureHearingRepository repository;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Mock
    private HearingManagementInterfaceApiClient hmiClient;

    @Mock
    private HearingRepository hearingRepository;

    @Mock
    private HearingStatusAuditServiceImpl hearingStatusAuditService;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        response = new AuthenticationResponse();
        repository = new DefaultFutureHearingRepository(activeDirectoryApiClient, applicationParams, hmiClient,
                                                        hearingRepository,hearingStatusAuditService, objectMapper);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
        given(hearingRepository.findById(Long.valueOf(CASE_LISTING_REQUEST_ID)))
            .willReturn(TestingUtil.hearingEntity());

        logger.setLevel(Level.INFO);
    }

    @Test
    void shouldSuccessfullyReturnAuthenticationObject() {
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        AuthenticationResponse testResponse = repository.retrieveAuthToken();
        assertEquals(response, testResponse);
    }

    @Test
    void shouldReturnSuccessfulPrivateHealthCheck() {
        HealthCheckResponse expectedResponse = new HealthCheckResponse();
        expectedResponse.setStatus(Status.UP);

        response.setAccessToken("test-token");

        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        given(hmiClient.privateHealthCheck("Bearer test-token")).willReturn(expectedResponse);

        HealthCheckResponse actualResponse = repository.privateHealthCheck();

        then(activeDirectoryApiClient).should().authenticate(requestString);
        then(hmiClient).should().privateHealthCheck("Bearer test-token");

        assertNotNull(actualResponse, "HealthCheckResponse should not be null");
        assertEquals(Status.UP, actualResponse.getStatus(), "HealthCheckResponse has unexpected status");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("activeDirectoryExceptions")
    void shouldFailPrivateHealthCheckActiveDirectory(Exception activeDirectoryException,
                                                     String expectedMessage,
                                                     Integer expectedErrorCode,
                                                     String expectedErrorDescription) {
        given(activeDirectoryApiClient.authenticate(requestString)).willThrow(activeDirectoryException);

        HealthCheckActiveDirectoryException healthCheckActiveDirectoryException =
            assertThrows(HealthCheckActiveDirectoryException.class,
                         () -> repository.privateHealthCheck());

        then(activeDirectoryApiClient).should().authenticate(requestString);

        assertEquals(expectedMessage,
                     healthCheckActiveDirectoryException.getMessage(),
                     "Exception has unexpected message");

        if (expectedErrorCode == null) {
            assertNull(healthCheckActiveDirectoryException.getErrorCode(),
                       "Exception error code should be null");
        } else {
            assertEquals(expectedErrorCode,
                         healthCheckActiveDirectoryException.getErrorCode(),
                         "Exception has unexpected error code");
        }

        if (expectedErrorDescription == null) {
            assertNull(healthCheckActiveDirectoryException.getErrorDescription(),
                       "Exception error description should be null");
        } else {
            assertEquals(expectedErrorDescription,
                         healthCheckActiveDirectoryException.getErrorDescription(),
                         "Exception has unexpected error description");
        }
    }

    @Test
    void shouldFailPrivateHealthCheckActiveDirectoryRequestBodyNull() {
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Request getTokenRequest =
            Request.create(POST, "/get/token/url", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);

        RetryableException retryableException =
            new RetryableException(400, "Connection/Read timeout", POST, null, 1L, getTokenRequest);

        given(activeDirectoryApiClient.authenticate(requestString)).willThrow(retryableException);

        HealthCheckActiveDirectoryException healthCheckActiveDirectoryException =
            assertThrows(HealthCheckActiveDirectoryException.class,
                         () -> repository.privateHealthCheck());

        then(activeDirectoryApiClient).should().authenticate(requestString);

        assertEquals("Connection/Read timeout",
                     healthCheckActiveDirectoryException.getMessage(),
                     "Health check exception has unexpected message");

        List<ILoggingEvent> logsList = listAppender.list;

        assertNotNull(logsList, "Log list should not be null");
        assertTrue(logsList.stream()
                       .anyMatch(log -> log.getLevel() == Level.DEBUG
                           && log.getFormattedMessage().equals("Request to Active Directory timed out - "
                                                                   + "URL: /get/token/url, Method: POST, Body: N/A")),
                   "Log list does not contain expected debug message");

        logger.detachAndStopAllAppenders();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("hmiExceptions")
    void shouldFailPrivateHealthCheckHmi(Exception hmiException,
                                         String expectedMessage,
                                         Integer expectedErrorCode,
                                         String expectedErrorDescription) {
        response.setAccessToken("test-token");

        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        given(hmiClient.privateHealthCheck("Bearer test-token")).willThrow(hmiException);

        HealthCheckHmiException healthCheckHmiException =
            assertThrows(HealthCheckHmiException.class,
                         () -> repository.privateHealthCheck());

        then(activeDirectoryApiClient).should().authenticate(requestString);
        then(hmiClient).should().privateHealthCheck("Bearer test-token");

        assertEquals(expectedMessage, healthCheckHmiException.getMessage(), "Exception has unexpected message");

        if (expectedErrorCode == null) {
            assertNull(healthCheckHmiException.getErrorCode(), "Exception error code should be null");
        } else {
            assertEquals(expectedErrorCode,
                         healthCheckHmiException.getErrorCode(),
                         "Exception has unexpected error code");
        }

        if (expectedErrorDescription == null) {
            assertNull(healthCheckHmiException.getErrorDescription(), "Exception error description should be null");
        } else {
            assertEquals(expectedErrorDescription,
                         healthCheckHmiException.getErrorDescription(),
                         "Exception has unexpected error description");
        }
    }

    @Test
    void shouldSuccessfullyCreateHearingRequest() {
        HearingManagementInterfaceResponse expectedResponse = new HearingManagementInterfaceResponse();
        expectedResponse.setResponseCode(202);
        response.setAccessToken("test-token");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(hmiClient.requestHearing("Bearer test-token", anyData))
            .willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.createHearingRequest(anyData,
                                                                                            CASE_LISTING_REQUEST_ID);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void shouldSuccessfullyAmendHearingRequest() {
        HearingManagementInterfaceResponse expectedResponse = new HearingManagementInterfaceResponse();
        expectedResponse.setResponseCode(202);
        response.setAccessToken("test-token");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(hmiClient.amendHearing(CASE_LISTING_REQUEST_ID, "Bearer test-token", anyData))
            .willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.amendHearingRequest(anyData,
                                                                                           CASE_LISTING_REQUEST_ID);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void shouldSuccessfullyDeleteHearingRequest() {
        HearingManagementInterfaceResponse expectedResponse = new HearingManagementInterfaceResponse();
        expectedResponse.setResponseCode(200);
        response.setAccessToken("test-token");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(hmiClient.deleteHearing(CASE_LISTING_REQUEST_ID, "Bearer test-token", anyData))
            .willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.deleteHearingRequest(anyData,
                                                                                           CASE_LISTING_REQUEST_ID);
        assertEquals(expectedResponse, actualResponse);
    }

    private static Stream<Arguments> activeDirectoryExceptions() {
        byte[] requestBody = "DummyGetTokenRequestBody".getBytes(StandardCharsets.UTF_8);
        final Request getTokenRequest =
            Request.create(POST, "/get/token/url", Collections.emptyMap(), requestBody, StandardCharsets.UTF_8, null);

        return Stream.of(
            arguments(named("BadFutureHearingRequestException: null error details",
                            new BadFutureHearingRequestException(EXCEPTION_MESSAGE_BAD_REQUEST, null)
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      null,
                      null),
            arguments(named("BadFutureHearingRequestException: error details with null error codes",
                            new BadFutureHearingRequestException(
                                EXCEPTION_MESSAGE_BAD_REQUEST,
                                createErrorDetailsAuthError(null, ERROR_DESCRIPTION_BAD_REQUEST)
                            )
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      null,
                      ERROR_DESCRIPTION_BAD_REQUEST),
            arguments(named("BadFutureHearingRequestException: error details with no error codes",
                            new BadFutureHearingRequestException(
                                EXCEPTION_MESSAGE_BAD_REQUEST,
                                createErrorDetailsAuthError(Collections.emptyList(), ERROR_DESCRIPTION_BAD_REQUEST)
                            )
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      null,
                      ERROR_DESCRIPTION_BAD_REQUEST),
            arguments(named("BadFutureHearingRequestException: error details with one error code",
                            new BadFutureHearingRequestException(
                                EXCEPTION_MESSAGE_BAD_REQUEST,
                                createErrorDetailsAuthError(List.of(1000), ERROR_DESCRIPTION_BAD_REQUEST)
                            )
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      1000,
                      ERROR_DESCRIPTION_BAD_REQUEST),
            arguments(named("BadFutureHearingRequestException: error details with two error codes",
                            new BadFutureHearingRequestException(
                                EXCEPTION_MESSAGE_BAD_REQUEST,
                                createErrorDetailsAuthError(List.of(2000, 3000), ERROR_DESCRIPTION_BAD_REQUEST)
                            )
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      2000,
                      ERROR_DESCRIPTION_BAD_REQUEST),
            arguments(named("AuthenticationException: null error details",
                            new AuthenticationException(EXCEPTION_MESSAGE_AUTH)
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      null,
                      null),
            arguments(named("AuthenticationException: error details with null error codes",
                            new AuthenticationException(
                                EXCEPTION_MESSAGE_AUTH,
                                createErrorDetailsAuthError(null, ERROR_DESCRIPTION_AUTH)
                            )
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      null,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("AuthenticationException: error details with no error codes",
                            new AuthenticationException(
                                EXCEPTION_MESSAGE_AUTH,
                                createErrorDetailsAuthError(Collections.emptyList(), ERROR_DESCRIPTION_AUTH)
                            )
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      null,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("AuthenticationException: error details with one error code",
                            new AuthenticationException(
                                EXCEPTION_MESSAGE_AUTH,
                                createErrorDetailsAuthError(List.of(4000), ERROR_DESCRIPTION_AUTH)
                            )
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      4000,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("AuthenticationException: error details with two error codes",
                            new AuthenticationException(
                                EXCEPTION_MESSAGE_AUTH,
                                createErrorDetailsAuthError(List.of(5000, 6000), ERROR_DESCRIPTION_AUTH)
                            )
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      5000,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("ResourceNotFoundException",
                            new ResourceNotFoundException("AD resource not found exception")
                      ),
                      "Resource not found",
                      null,
                      null),
            arguments(named("RetryableException",
                            new RetryableException(400, "Connection/Read timeout", POST, null, 1L, getTokenRequest)
                      ),
                      "Connection/Read timeout",
                      null,
                      null)
        );
    }

    private static Stream<Arguments> hmiExceptions() {
        return Stream.of(
            arguments(named("BadFutureHearingRequestException: null error details",
                            new BadFutureHearingRequestException(EXCEPTION_MESSAGE_BAD_REQUEST, null)
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      null,
                      null),
            arguments(named("BadFutureHearingRequestException: error details",
                            new BadFutureHearingRequestException(
                                EXCEPTION_MESSAGE_BAD_REQUEST,
                                createErrorDetailsApiError(100, ERROR_DESCRIPTION_BAD_REQUEST))
                      ),
                      EXCEPTION_MESSAGE_BAD_REQUEST,
                      100,
                      ERROR_DESCRIPTION_BAD_REQUEST),
            arguments(named("AuthenticationException: null error details",
                            new AuthenticationException(EXCEPTION_MESSAGE_AUTH)
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      null,
                      null),
            arguments(named("AuthenticationException: error details",
                            new AuthenticationException(
                                EXCEPTION_MESSAGE_AUTH,
                                createErrorDetailsApiError(200, ERROR_DESCRIPTION_AUTH))
                      ),
                      EXCEPTION_MESSAGE_AUTH,
                      200,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("ResourceNotFoundException",
                            new ResourceNotFoundException("HMI resource not found exception")
                      ),
                      "Resource not found",
                      null,
                      null)
        );
    }

    private static ErrorDetails createErrorDetailsAuthError(List<Integer> errorCodes, String errorDescription) {
        ErrorDetails errorDetails = new ErrorDetails();

        errorDetails.setAuthErrorCodes(errorCodes);
        errorDetails.setAuthErrorDescription(errorDescription);

        return errorDetails;
    }

    private static ErrorDetails createErrorDetailsApiError(Integer errorCode, String errorDescription) {
        ErrorDetails errorDetails = new ErrorDetails();

        errorDetails.setApiStatusCode(errorCode);
        errorDetails.setApiErrorMessage(errorDescription);

        return errorDetails;
    }
}

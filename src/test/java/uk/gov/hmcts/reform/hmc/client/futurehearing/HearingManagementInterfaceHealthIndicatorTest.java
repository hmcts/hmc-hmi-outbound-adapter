package uk.gov.hmcts.reform.hmc.client.futurehearing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckActiveDirectoryException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckHmiException;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingManagementInterfaceHealthIndicatorTest {

    private static final String BAD_FUTURE_REQUEST_EXCEPTION_MESSAGE = "Missing or invalid request parameters";

    private static final Integer ACTIVE_DIRECTORY_ERROR_CODE_SCOPE_INVALID = 1002012;
    private static final String ACTIVE_DIRECTORY_ERROR_DESC_SCOPE_INVALID =
        "AADSTS1002012: The provided value for scope scope is not valid.";

    private static final Integer ACTIVE_DIRECTORY_ERROR_CODE_CLIENT_SECRET_INVALID = 7000215;
    private static final String ACTIVE_DIRECTORY_ERROR_DESC_CLIENT_SECRET_INVALID =
        "AADSTS7000215: Invalid client secret provided.";

    private static final String AUTH_EXCEPTION_MESSAGE = "Authentication error";

    private static final String HMI_ERROR_DESC_MISSING_INVALID_HEADER_SOURCE = "Missing/Invalid Header Source-System";

    private static final String HMI_ERROR_DESC_MISSING_OAUTH_INFO = "Access denied due to invalid OAuth information";

    private static final String RESOURCE_NOT_FOUND_EXCEPTION_MESSAGE = "Resource not found";

    private static final String KEY_MESSAGE = "message";
    private static final String KEY_API_NAME = "apiName";
    private static final String KEY_ERROR_CODE = "errorCode";
    private static final String KEY_ERROR_DESCRIPTION = "errorDescription";

    private static final String API_NAME_ACTIVE_DIRECTORY = "ActiveDirectory";
    private static final String API_NAME_HMI = "HearingManagementInterface";

    @Mock
    private DefaultFutureHearingRepository futureHearingRepository;

    private HearingManagementInterfaceHealthIndicator hmiHealthIndicator;

    @BeforeEach
    void setUp() {
        hmiHealthIndicator = new HearingManagementInterfaceHealthIndicator(futureHearingRepository);
    }

    @ParameterizedTest
    @MethodSource("healthCheckStatuses")
    void healthShouldMatchHealthCheckStatus(Status healthStatus) {
        HealthCheckResponse response = new HealthCheckResponse();
        response.setStatus(healthStatus);

        when(futureHearingRepository.privateHealthCheck()).thenReturn(response);

        Health health = hmiHealthIndicator.health();

        assertEquals(healthStatus, health.getStatus(), "Health status has unexpected value");

        verify(futureHearingRepository).privateHealthCheck();
    }

    @ParameterizedTest
    @MethodSource("healthCheckActiveDirectoryExceptions")
    void healthShouldBeDownForHealthCheckActiveDirectoryExceptions(HealthCheckActiveDirectoryException exception,
                                                                   Map<String, Object> expectedHealthDetails) {
        assertHealthDown(exception, expectedHealthDetails);
    }

    @ParameterizedTest
    @MethodSource("healthCheckHmiExceptions")
    void healthShouldBeDownForHealthCheckHmiExceptions(HealthCheckHmiException exception,
                                                       Map<String, Object> expectedHealthDetails) {
        assertHealthDown(exception, expectedHealthDetails);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("nonHealthCheckExceptions")
    void healthShouldBeDownForNonHealthCheckExceptions(Exception exception, Map<String, Object> expectedHealthDetails) {
        assertHealthDown(exception, expectedHealthDetails);
    }

    private void assertHealthDown(Exception exception, Map<String, Object> expectedHealthDetails) {
        when(futureHearingRepository.privateHealthCheck()).thenThrow(exception);

        Health health = hmiHealthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus(), "Health status should be DOWN");

        Map<String, Object> actualDetails = health.getDetails();
        assertNotNull(actualDetails, "Details should not be null");
        assertEquals(expectedHealthDetails.size(),
                     actualDetails.size(),
                     "Details does not contain expected number of items");

        for (String key : expectedHealthDetails.keySet()) {
            assertTrue(actualDetails.containsKey(key), "Details should contain an entry for '" + key + "'");
            assertEquals(expectedHealthDetails.get(key),
                         actualDetails.get(key),
                         "Details entry for '" + key + "' has unexpected value");
        }

        verify(futureHearingRepository).privateHealthCheck();
    }

    private static Stream<Arguments> healthCheckStatuses() {
        return Stream.of(
            arguments(Status.UP),
            arguments(Status.DOWN),
            arguments(Status.UNKNOWN),
            arguments(Status.OUT_OF_SERVICE)
        );
    }

    private static Stream<Arguments> healthCheckActiveDirectoryExceptions() {
        Map<String, Object> expectedDetailsResourceNotFound = new HashMap<>();
        expectedDetailsResourceNotFound.put(KEY_MESSAGE, RESOURCE_NOT_FOUND_EXCEPTION_MESSAGE);
        expectedDetailsResourceNotFound.put(KEY_API_NAME, API_NAME_ACTIVE_DIRECTORY);

        return Stream.of(
            arguments(new HealthCheckActiveDirectoryException(BAD_FUTURE_REQUEST_EXCEPTION_MESSAGE,
                                                              ACTIVE_DIRECTORY_ERROR_CODE_SCOPE_INVALID,
                                                              ACTIVE_DIRECTORY_ERROR_DESC_SCOPE_INVALID
                      ),
                      createExpectedDetails(BAD_FUTURE_REQUEST_EXCEPTION_MESSAGE,
                                            API_NAME_ACTIVE_DIRECTORY,
                                            ACTIVE_DIRECTORY_ERROR_CODE_SCOPE_INVALID,
                                            ACTIVE_DIRECTORY_ERROR_DESC_SCOPE_INVALID)
            ),
            arguments(new HealthCheckActiveDirectoryException(AUTH_EXCEPTION_MESSAGE,
                                                              ACTIVE_DIRECTORY_ERROR_CODE_CLIENT_SECRET_INVALID,
                                                              ACTIVE_DIRECTORY_ERROR_DESC_CLIENT_SECRET_INVALID
                      ),
                      createExpectedDetails(AUTH_EXCEPTION_MESSAGE,
                                            API_NAME_ACTIVE_DIRECTORY,
                                            ACTIVE_DIRECTORY_ERROR_CODE_CLIENT_SECRET_INVALID,
                                            ACTIVE_DIRECTORY_ERROR_DESC_CLIENT_SECRET_INVALID)
            ),
            arguments(new HealthCheckActiveDirectoryException(RESOURCE_NOT_FOUND_EXCEPTION_MESSAGE),
                      expectedDetailsResourceNotFound)
        );
    }

    private static Stream<Arguments> healthCheckHmiExceptions() {
        Map<String, Object> expectedDetailsResourceNotFound = new HashMap<>();
        expectedDetailsResourceNotFound.put(KEY_MESSAGE, RESOURCE_NOT_FOUND_EXCEPTION_MESSAGE);
        expectedDetailsResourceNotFound.put(KEY_API_NAME, API_NAME_HMI);

        return Stream.of(
            arguments(new HealthCheckHmiException(BAD_FUTURE_REQUEST_EXCEPTION_MESSAGE,
                                                  400,
                                                  HMI_ERROR_DESC_MISSING_INVALID_HEADER_SOURCE
                      ),
                      createExpectedDetails(BAD_FUTURE_REQUEST_EXCEPTION_MESSAGE,
                                            API_NAME_HMI,
                                            400,
                                            HMI_ERROR_DESC_MISSING_INVALID_HEADER_SOURCE
                      )),
            arguments(new HealthCheckHmiException(AUTH_EXCEPTION_MESSAGE,
                                                  401,
                                                  HMI_ERROR_DESC_MISSING_OAUTH_INFO
                      ),
                      createExpectedDetails(AUTH_EXCEPTION_MESSAGE,
                                            API_NAME_HMI,
                                            401,
                                            HMI_ERROR_DESC_MISSING_OAUTH_INFO
                      )),
            arguments(new HealthCheckHmiException(RESOURCE_NOT_FOUND_EXCEPTION_MESSAGE),
                      expectedDetailsResourceNotFound)
        );
    }

    private static Stream<Arguments> nonHealthCheckExceptions() {
        Map<String, Object> expectedDetailsRuntimeWithMessage = new HashMap<>();
        expectedDetailsRuntimeWithMessage.put(KEY_MESSAGE, "Runtime exception");

        Map<String, Object> expectedDetailsRuntimeExceptionWithoutMessage = Collections.emptyMap();

        return Stream.of(
            arguments(named("Runtime exception with message", new RuntimeException("Runtime exception")),
                      expectedDetailsRuntimeWithMessage),
            arguments(named("Runtime exception without message", new RuntimeException()),
                      expectedDetailsRuntimeExceptionWithoutMessage)
        );
    }

    private static Map<String, Object> createExpectedDetails(String message,
                                                             String apiName,
                                                             Integer errorCode,
                                                             String errorDescription) {
        Map<String, Object> expectedDetails = new HashMap<>();

        expectedDetails.put(KEY_MESSAGE, message);
        expectedDetails.put(KEY_API_NAME, apiName);
        expectedDetails.put(KEY_ERROR_CODE, errorCode);
        expectedDetails.put(KEY_ERROR_DESCRIPTION, errorDescription);

        return expectedDetails;
    }
}

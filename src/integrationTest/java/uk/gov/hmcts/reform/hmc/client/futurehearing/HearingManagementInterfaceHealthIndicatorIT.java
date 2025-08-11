package uk.gov.hmcts.reform.hmc.client.futurehearing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.hmc.BaseTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnToken;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnTokenTimeout;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheck;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheckThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyReturnToken;

// Set future-hearing-api read timeout value to force a timeout during timeout test
@TestPropertySource(
    properties = {
        "spring.cloud.openfeign.client.config.future-hearing-api.readTimeout = 10"
    }
)
class HearingManagementInterfaceHealthIndicatorIT extends BaseTest {

    private static final String TEST_TOKEN = "test-token";

    private static final String KEY_MESSAGE = "message";
    private static final String KEY_API_NAME = "apiName";
    private static final String KEY_ERROR_CODE = "errorCode";
    private static final String KEY_ERROR_DESC = "errorDescription";

    private static final String API_NAME_AD = "ActiveDirectory";
    private static final String API_NAME_HMI = "HearingManagementInterface";

    private final HearingManagementInterfaceHealthIndicator hmiHealthIndicator;

    @Autowired
    public HearingManagementInterfaceHealthIndicatorIT(HearingManagementInterfaceHealthIndicator hmiHealthIndicator) {
        this.hmiHealthIndicator = hmiHealthIndicator;
    }

    @ParameterizedTest
    @MethodSource("healthStatuses")
    void healthShouldMatchHealthCheckStatus(Status healthStatus) {
        stubSuccessfullyReturnToken(TEST_TOKEN);
        stubHealthCheck(TEST_TOKEN, healthStatus);

        Health health = hmiHealthIndicator.health();

        assertEquals(healthStatus, health.getStatus(), "Health status has unexpected value");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("adApiErrors")
    void healthShouldBeDownForActiveDirectoryApiError(int statusCode,
                                                      String errorDescription,
                                                      List<Integer> errorCodes,
                                                      Map<String, Object> expectedHealthDetails) {
        stubFailToReturnToken(statusCode, errorDescription, errorCodes);

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, expectedHealthDetails);
    }

    @Test
    void healthShouldBeDownForActiveDirectoryApiTimeout() {
        stubFailToReturnTokenTimeout();

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, createExpectedHealthDetails("Connection/Read timeout", API_NAME_AD));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("hmiApiErrors")
    void healthShouldBeDownForHmiApiError(int statusCode,
                                          String errorMessage,
                                          Map<String, Object> expectedHealthDetails) {
        stubSuccessfullyReturnToken(TEST_TOKEN);
        stubHealthCheckThrowingError(statusCode, errorMessage);

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, expectedHealthDetails);
    }

    private static Stream<Arguments> healthStatuses() {
        return Stream.of(
            arguments(Status.UP),
            arguments(Status.DOWN),
            arguments(Status.UNKNOWN),
            arguments(Status.OUT_OF_SERVICE)
        );
    }

    private static Stream<Arguments> adApiErrors() {
        return Stream.of(
            arguments(named("Bad request error", 400),
                      "AADSTS1002012: The provided value for scope scope is not valid.",
                      List.of(1002012),
                      createExpectedHealthDetails("Missing or invalid request parameters",
                                                  API_NAME_AD,
                                                  1002012,
                                                  "AADSTS1002012: The provided value for scope scope is not valid.")
            ),
            arguments(named("Unauthorised error", 401),
                      "AADSTS7000215: Invalid client secret provided.",
                      List.of(7000215),
                      createExpectedHealthDetails("Authentication error",
                                                  API_NAME_AD,
                                                  7000215,
                                                  "AADSTS7000215: Invalid client secret provided.")
            ),
            arguments(named("Resource not found error", 404),
                      "AD Resource not found",
                      List.of(1000000),
                      createExpectedHealthDetails("Resource not found", API_NAME_AD)
            ),
            arguments(named("Internal server error", 500),
                      "AD Internal server error",
                      List.of(2000000),
                      createExpectedHealthDetails("Server error", API_NAME_AD, 2000000, "AD Internal server error")
            )
        );
    }

    private static Stream<Arguments> hmiApiErrors() {
        return Stream.of(
            arguments(named("Bad request error", 400),
                      "Missing/Invalid Header Source-System",
                      createExpectedHealthDetails("Missing or invalid request parameters",
                                                  API_NAME_HMI,
                                                  400,
                                                  "Missing/Invalid Header Source-System")
            ),
            arguments(named("Unauthorised error", 401),
                      "Access denied due to invalid OAuth information",
                      createExpectedHealthDetails("Authentication error",
                                                  API_NAME_HMI,
                                                  401,
                                                  "Access denied due to invalid OAuth information")
            ),
            arguments(named("Resource not found error", 404),
                      "HMI Resource not found",
                      createExpectedHealthDetails("Resource not found", API_NAME_HMI)
            ),
            arguments(named("Internal server error", 500),
                      "Internal server error",
                      createExpectedHealthDetails("Server error",
                                                  API_NAME_HMI,
                                                  500,
                                                  "Internal server error")
            )
        );
    }

    private static Map<String, Object> createExpectedHealthDetails(String message,
                                                                   String apiName,
                                                                   Integer errorCode,
                                                                   String errorDescription) {
        Map<String, Object> expectedHealthDetails = new HashMap<>();

        expectedHealthDetails.put(KEY_MESSAGE, message);
        expectedHealthDetails.put(KEY_API_NAME, apiName);
        expectedHealthDetails.put(KEY_ERROR_CODE, errorCode);
        expectedHealthDetails.put(KEY_ERROR_DESC, errorDescription);

        return expectedHealthDetails;
    }

    private static Map<String, Object> createExpectedHealthDetails(String message, String apiName) {
        Map<String, Object> expectedHealthDetails = new HashMap<>();

        expectedHealthDetails.put(KEY_MESSAGE, message);
        expectedHealthDetails.put(KEY_API_NAME, apiName);

        return expectedHealthDetails;
    }

    private void assertHealthDown(Health health, Map<String, Object> expectedHealthDetails) {
        assertEquals(Status.DOWN, health.getStatus(), "Health status should be DOWN");

        Map<String, Object> actualHealthDetails = health.getDetails();
        assertNotNull(actualHealthDetails, "Health details should not be null");
        assertEquals(expectedHealthDetails.size(),
                     actualHealthDetails.size(),
                     "Health details does not contain expected number of items");

        for (String key : expectedHealthDetails.keySet()) {
            assertTrue(actualHealthDetails.containsKey(key),
                       "Health details should contain an entry for '" + key + "'");
            assertEquals(expectedHealthDetails.get(key),
                         actualHealthDetails.get(key),
                         "Details entry for '" + key + "' has unexpected value");
        }
    }
}

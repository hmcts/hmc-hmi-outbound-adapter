package uk.gov.hmcts.reform.hmc.client.futurehearing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.hmc.BaseTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnToken;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnTokenHtmlResponse;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnTokenTimeout;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheck;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheckThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubHealthCheckThrowingErrorHtmlResponse;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyReturnToken;

// Set future-hearing-api read timeout value to force a timeout during timeout test
@TestPropertySource(
    properties = {
        "spring.cloud.openfeign.client.config.future-hearing-api.readTimeout = 10"
    }
)
class HearingManagementInterfaceHealthIndicatorIT extends BaseTest {

    private static final String TEST_TOKEN = "test-token";
    private static final String SERVER_ERROR = "Server error";
    private static final String HTML_INTERNAL_SERVER_ERROR =
        "<html><head><title>500 Internal Server Error</title></head><body><h1>Internal Server Error</h1></body></html>";

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
    @MethodSource("uk.gov.hmcts.reform.hmc.utils.TestingUtil#healthStatuses")
    void healthShouldMatchHealthCheckStatus(Status healthStatus) {
        stubSuccessfullyReturnToken(TEST_TOKEN);
        stubHealthCheck(TEST_TOKEN, healthStatus);

        Health health = hmiHealthIndicator.health();

        assertEquals(healthStatus, health.getStatus(), "Health status has unexpected value");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("uk.gov.hmcts.reform.hmc.utils.TestingUtil#adApiErrorsAndExpectedHealthCheckValues")
    void healthShouldBeDownForActiveDirectoryApiError(int responseStatusCode,
                                                      String responseErrorDescription,
                                                      List<Integer> responseErrorCodes,
                                                      String apiName,
                                                      String message,
                                                      Integer errorCode,
                                                      String errorDescription) {
        stubFailToReturnToken(responseStatusCode, responseErrorDescription, responseErrorCodes);

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, apiName, message, errorCode, errorDescription);
    }

    @Test
    void healthShouldBeDownForActiveDirectoryApiErrorNonJson() {
        stubFailToReturnTokenHtmlResponse(500, HTML_INTERNAL_SERVER_ERROR);

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, API_NAME_AD, SERVER_ERROR, 500, HTML_INTERNAL_SERVER_ERROR);
    }

    @Test
    void healthShouldBeDownForActiveDirectoryApiTimeout() {
        stubFailToReturnTokenTimeout();

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, API_NAME_AD, "Connection/Read timeout", null, null);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("uk.gov.hmcts.reform.hmc.utils.TestingUtil#hmiApiErrorsAndExpectedHealthCheckValues")
    void healthShouldBeDownForHmiApiError(int responseStatusCode,
                                          String responseErrorMessage,
                                          String apiName,
                                          String message,
                                          Integer errorCode,
                                          String errorDescription) {
        stubSuccessfullyReturnToken(TEST_TOKEN);
        stubHealthCheckThrowingError(responseStatusCode, responseErrorMessage);

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, apiName, message, errorCode, errorDescription);
    }

    @Test
    void healthShouldBeDownForHmiApiErrorNonJson() {
        stubSuccessfullyReturnToken(TEST_TOKEN);
        stubHealthCheckThrowingErrorHtmlResponse(500, HTML_INTERNAL_SERVER_ERROR);

        Health health = hmiHealthIndicator.health();

        assertHealthDown(health, API_NAME_HMI, SERVER_ERROR, 500, HTML_INTERNAL_SERVER_ERROR);
    }

    private void assertHealthDown(Health health,
                                  String apiName,
                                  String message,
                                  Integer statusCode,
                                  String errorDescription) {
        assertEquals(Status.DOWN, health.getStatus(), "Health status should be DOWN");

        Map<String, Object> actualHealthDetails = health.getDetails();
        assertNotNull(actualHealthDetails, "Health details should not be null");

        assertHealthEntry(actualHealthDetails, KEY_API_NAME, apiName);
        assertHealthEntry(actualHealthDetails, KEY_MESSAGE, message);
        assertHealthEntry(actualHealthDetails, KEY_ERROR_CODE, statusCode);
        assertHealthEntry(actualHealthDetails, KEY_ERROR_DESC, errorDescription);
    }

    private void assertHealthEntry(Map<String, Object> healthDetails, String key, Object value) {
        if (value == null) {
            assertFalse(healthDetails.containsKey(key), "Health details should not contain an entry for '" + key + "'");
        } else {
            assertTrue(healthDetails.containsKey(key), "Health details should contain an entry for '" + key + "'");
            assertEquals(value, healthDetails.get(key), "Health details entry for '" + key + "' has unexpected value");
        }
    }
}

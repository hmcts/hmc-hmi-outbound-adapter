package uk.gov.hmcts.reform;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationParamsTest {

    private final ApplicationParams applicationParams = new ApplicationParams();
    private static final String VALUE = "test-value";

    @Test
    void shouldGetClientId() {
        ReflectionTestUtils.setField(applicationParams, "clientId", VALUE);
        assertEquals(VALUE,
                     applicationParams.getClientId());
    }

    @Test
    void shouldGetClientSecret() {
        ReflectionTestUtils.setField(applicationParams, "clientSecret", VALUE);
        assertEquals(VALUE,
                     applicationParams.getClientSecret());
    }

    @Test
    void shouldGetGrantType() {
        ReflectionTestUtils.setField(applicationParams, "grantType", VALUE);
        assertEquals(VALUE,
                     applicationParams.getGrantType());
    }

    @Test
    void shouldGetScope() {
        ReflectionTestUtils.setField(applicationParams, "scope", VALUE);
        assertEquals(VALUE,
                     applicationParams.getScope());
    }

    @Test
    void shouldGetSourceSystem() {
        ReflectionTestUtils.setField(applicationParams, "sourceSystem", VALUE);
        assertEquals(VALUE,
                     applicationParams.getSourceSystem());
    }

    @Test
    void shouldGetDestinationSystem() {
        ReflectionTestUtils.setField(applicationParams, "destinationSystem", VALUE);
        assertEquals(
            VALUE,
            applicationParams.getDestinationSystem()
        );
    }

    @Test
    void shouldGetOutboundConnectionString() {
        ReflectionTestUtils.setField(applicationParams, "outboundConnectionString", VALUE);
        assertEquals(VALUE,
                     applicationParams.getOutboundConnectionString());
    }

    @Test
    void shouldGetOutboundQueueName() {
        ReflectionTestUtils.setField(applicationParams, "outboundQueueName", VALUE);
        assertEquals(VALUE,
                     applicationParams.getOutboundQueueName());
    }

    @Test
    void shouldGetInboundConnectionString() {
        ReflectionTestUtils.setField(applicationParams, "inboundConnectionString", VALUE);
        assertEquals(VALUE,
                     applicationParams.getInboundConnectionString());
    }

    @Test
    void shouldGetInboundQueueName() {
        ReflectionTestUtils.setField(applicationParams, "inboundQueueName", VALUE);
        assertEquals(VALUE,
                     applicationParams.getInboundQueueName());
    }

    @Test
    void shouldGetExponentialMultiplier() {
        ReflectionTestUtils.setField(applicationParams, "exponentialMultiplier", VALUE);
        assertEquals(VALUE,
                     applicationParams.getExponentialMultiplier());
    }

    @Test
    void shouldGetExponentialMultiplierMaxRetries() {
        ReflectionTestUtils.setField(applicationParams, "exponentialMultiplierMaxRetries", VALUE);
        assertEquals(VALUE,
            applicationParams.getExponentialMultiplierMaxRetries());
    }

    @Test void shouldGetMaxRetryAttempts() {
        ReflectionTestUtils.setField(applicationParams, "maxRetryAttempts", 5);
        assertEquals(5, applicationParams.getMaxRetryAttempts());
    }
}

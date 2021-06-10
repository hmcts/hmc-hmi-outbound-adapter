package uk.gov.hmcts.reform;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationParamsTest {

    private ApplicationParams applicationParams = new ApplicationParams();

    @Test
    void shouldGetClientId() {
        ReflectionTestUtils.setField(applicationParams, "clientId", "someurl");
        assertEquals("someurl",
                     applicationParams.getClientId());
    }

    @Test
    void shouldGetClientSecret() {
        ReflectionTestUtils.setField(applicationParams, "clientSecret", "someurl");
        assertEquals("someurl",
                     applicationParams.getClientSecret());
    }

    @Test
    void shouldGetGrantType() {
        ReflectionTestUtils.setField(applicationParams, "grantType", "someurl");
        assertEquals("someurl",
                     applicationParams.getGrantType());
    }

    @Test
    void shouldGetScope() {
        ReflectionTestUtils.setField(applicationParams, "scope", "someurl");
        assertEquals("someurl",
                     applicationParams.getScope());
    }

    @Test
    void shouldGetConnectionString() {
        ReflectionTestUtils.setField(applicationParams, "connectionString", "someValue");
        assertEquals("someValue",
                     applicationParams.getConnectionString());
    }

    @Test
    void shouldGetQueueName() {
        ReflectionTestUtils.setField(applicationParams, "queueName", "someValue");
        assertEquals("someValue",
                     applicationParams.getQueueName());
    }

    @Test
    void shouldGetWaitToRetryTime() {
        ReflectionTestUtils.setField(applicationParams, "waitToRetryTime", "someValue");
        assertEquals("someValue",
                     applicationParams.getWaitToRetryTime());
    }
}

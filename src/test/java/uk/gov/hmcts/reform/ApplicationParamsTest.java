package uk.gov.hmcts.reform;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationParamsTest {

    private final ApplicationParams applicationParams = new ApplicationParams();

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

package uk.gov.hmcts.reform.hmc.client.futurehearing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthCheckResponseTest {

    private HealthCheckResponse healthCheckResponse;

    @BeforeEach
    void setUp() {
        healthCheckResponse = new HealthCheckResponse();
    }

    @Test
    void shouldSetStatus() {
        healthCheckResponse.setStatus(Status.UP);
        assertEquals(Status.UP, healthCheckResponse.getStatus(), "HealthCheckResponse has unexpected status");
    }
}

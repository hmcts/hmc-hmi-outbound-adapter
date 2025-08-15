package uk.gov.hmcts.reform.hmc.client.futurehearing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckException;
import uk.gov.hmcts.reform.hmc.repository.FutureHearingRepository;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HearingManagementInterfaceHealthIndicator implements HealthIndicator {

    private final FutureHearingRepository futureHearingRepository;

    @Autowired
    public HearingManagementInterfaceHealthIndicator(FutureHearingRepository futureHearingRepository) {
        this.futureHearingRepository = futureHearingRepository;
    }

    @Override
    public Health health() {
        try {
            log.debug("Checking HMI health");
            HealthCheckResponse response = futureHearingRepository.privateHealthCheck();
            log.debug("HMI health check completed successfully");

            return new Health.Builder(response.getStatus()).build();
        } catch (HealthCheckException e) {
            log.error("HMI health check failed");
            return healthDown(e);
        } catch (Exception e) {
            log.error("HMI health check failed, exception: {}, message: {}",
                      e.getClass().getSimpleName(),
                      e.getMessage());
            return healthDown(e);
        }
    }

    private Health healthDown(HealthCheckException healthCheckException) {
        Map<String, Object> details = new HashMap<>();

        addDetailIfNotNull(details, "message", healthCheckException.getMessage());
        details.put("apiName", healthCheckException.getApiName());
        addDetailIfNotNull(details, "errorCode", healthCheckException.getErrorCode());
        addDetailIfNotNull(details, "errorDescription", healthCheckException.getErrorDescription());

        return buildHealthDown(details);
    }

    private Health healthDown(Exception exception) {
        Map<String, Object> details = new HashMap<>();

        addDetailIfNotNull(details, "message", exception.getMessage());

        return buildHealthDown(details);
    }

    private void addDetailIfNotNull(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private Health buildHealthDown(Map<String, Object> details) {
        return new Health.Builder()
            .down()
            .withDetails(details)
            .build();
    }
}

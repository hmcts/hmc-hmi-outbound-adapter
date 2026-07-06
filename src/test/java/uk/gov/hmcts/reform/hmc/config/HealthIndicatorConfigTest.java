package uk.gov.hmcts.reform.hmc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceHealthIndicator;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class HealthIndicatorConfigTest {

    @Mock
    private DefaultFutureHearingRepository futureHearingRepository;

    private HealthIndicatorConfig healthIndicatorConfig;

    @BeforeEach
    void setUp() {
        healthIndicatorConfig = new HealthIndicatorConfig();
    }

    @Test
    void shouldCreateHearingManagementInterfaceHealthIndicator() {
        HearingManagementInterfaceHealthIndicator healthIndicator =
            healthIndicatorConfig.hearingManagementInterfaceHealthIndicator(futureHearingRepository);

        assertNotNull(healthIndicator, "HearingManagementHealthIndicator should be created");
    }
}

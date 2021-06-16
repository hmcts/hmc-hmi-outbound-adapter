package uk.gov.hmcts.reform.hmc.client.futurehearing;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class HearingManagementInterfaceRequestInterceptor implements RequestInterceptor {

    private final ApplicationParams applicationParams;
    private final Clock clock;

    public HearingManagementInterfaceRequestInterceptor(ApplicationParams applicationParams,
                                                        @Qualifier("utcClock") Clock clock) {
        this.applicationParams = applicationParams;
        this.clock = clock;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!template.headers().containsKey("Source-System")) {
            template.header("Source-System", applicationParams.getSourceSystem());
        }
        if (!template.headers().containsKey("Destination-System")) {
            template.header("Destination-System", applicationParams.getDestinationSystem());
        }
        if (!template.headers().containsKey("Request-Created-At")) {
            template.header("Request-Created-At", Instant.now(clock).toString());
        }
        if (!template.headers().containsKey("transactionIdHMCTS")) {
            template.header("transactionIdHMCTS", String.valueOf(UUID.randomUUID()));
        }
    }
}

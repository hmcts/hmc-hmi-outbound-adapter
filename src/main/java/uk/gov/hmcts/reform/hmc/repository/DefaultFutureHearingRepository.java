package uk.gov.hmcts.reform.hmc.repository;

import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationRequest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingApiClient;

@Repository("defaultFutureHearingRepository")
public class DefaultFutureHearingRepository implements FutureHearingRepository {

    private final FutureHearingApiClient futureHearingApiClient;
    private final ApplicationParams applicationParams;

    public DefaultFutureHearingRepository(FutureHearingApiClient futureHearingApiClient,
                                          ApplicationParams applicationParams) {
        this.futureHearingApiClient = futureHearingApiClient;
        this.applicationParams = applicationParams;
    }

    public AuthenticationResponse retrieveAuthToken() {
        return futureHearingApiClient.authenticate(
            new AuthenticationRequest(
                applicationParams.getGrantType(),
                applicationParams.getClientId(), applicationParams.getScope(),
                applicationParams.getClientSecret()
            ).getRequest());
    }
}

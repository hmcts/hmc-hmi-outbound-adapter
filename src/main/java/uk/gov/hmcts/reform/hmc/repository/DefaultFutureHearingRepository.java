package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationRequest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

@Repository("defaultFutureHearingRepository")
public class DefaultFutureHearingRepository implements FutureHearingRepository {

    private final HearingManagementInterfaceApiClient hmiClient;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final ApplicationParams applicationParams;
    private static final String BEARER = "Bearer ";

    public DefaultFutureHearingRepository(ActiveDirectoryApiClient activeDirectoryApiClient,
                                          ApplicationParams applicationParams,
                                          HearingManagementInterfaceApiClient hmiClient) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
    }

    public AuthenticationResponse retrieveAuthToken() {
        return activeDirectoryApiClient.authenticate(
            new AuthenticationRequest(
                applicationParams.getGrantType(),
                applicationParams.getClientId(), applicationParams.getScope(),
                applicationParams.getClientSecret()
            ).getRequest());
    }

    @Override
    public HearingManagementInterfaceResponse createHearingRequest(JsonNode data) {
        String authorization = retrieveAuthToken().getAccessToken();
        return hmiClient.requestHearing(BEARER + authorization, data);
    }

    @Override
    public HearingManagementInterfaceResponse amendHearingRequest(JsonNode data, String caseListingRequestId) {
        String authorization = retrieveAuthToken().getAccessToken();
        return hmiClient.amendHearing(caseListingRequestId, BEARER + authorization, data);
    }

    @Override
    public HearingManagementInterfaceResponse deleteHearingRequest(JsonNode data, String caseListingRequestId) {
        String authorization = retrieveAuthToken().getAccessToken();
        return hmiClient.deleteHearing(caseListingRequestId, BEARER + authorization, data);
    }
}

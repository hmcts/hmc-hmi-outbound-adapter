package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationRequest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Repository("defaultFutureHearingRepository")
public class DefaultFutureHearingRepository implements FutureHearingRepository {

    private final Clock clock;
    private final HearingManagementInterfaceApiClient hmiClient;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final ApplicationParams applicationParams;

    public static final String REQUEST_ID_NOT_FOUND = "Case Listing Request Id cannot be retrieved from message";

    public DefaultFutureHearingRepository(ActiveDirectoryApiClient activeDirectoryApiClient,
                                          ApplicationParams applicationParams,
                                          HearingManagementInterfaceApiClient hmiClient,
                                          @Qualifier("utcClock") Clock clock) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
        this.clock = clock;
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
        return hmiClient.requestHearing("Bearer " + authorization, applicationParams.getSourceSystem(),
                                        applicationParams.getDestinationSystem(), Instant.now(clock).toString(),
                                        UUID.randomUUID(), data);
    }

    @Override
    public HearingManagementInterfaceResponse amendHearingRequest(JsonNode data) {
        String authorization = retrieveAuthToken().getAccessToken();

        String caseListingRequestId = getCaseListingRequestId(data);

        return hmiClient.amendHearing(caseListingRequestId, "Bearer " + authorization,
                                      applicationParams.getSourceSystem(), applicationParams.getDestinationSystem(),
                                      Instant.now(clock).toString(), UUID.randomUUID(), data);
    }

    public String getCaseListingRequestId(JsonNode data) {
        String caseListingRequestId;
        try {
            caseListingRequestId = data.get("hearingRequest").get("_case").get("caseListingRequestId").asText();
        } catch (NullPointerException exception) {
            throw new AuthenticationException(REQUEST_ID_NOT_FOUND);
        }
        return caseListingRequestId;
    }
}

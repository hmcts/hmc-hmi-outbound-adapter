package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationRequest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAudit;
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditService;

import java.time.LocalDateTime;

@Slf4j
@Repository("defaultFutureHearingRepository")
public class DefaultFutureHearingRepository implements FutureHearingRepository {

    private final HearingManagementInterfaceApiClient hmiClient;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final ApplicationParams applicationParams;
    private static final String BEARER = "Bearer ";
    private final HearingStatusAuditService hearingStatusAuditService;

    public DefaultFutureHearingRepository(ActiveDirectoryApiClient activeDirectoryApiClient,
                                          ApplicationParams applicationParams,
                                          HearingManagementInterfaceApiClient hmiClient,
                                          HearingStatusAuditService hearingStatusAuditService) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
        this.hearingStatusAuditService = hearingStatusAuditService;
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
        log.debug("CreateHearingRequest sent to FH : {}", data.toString());
        // TODO validate the mapped values
        hearingStatusAuditService.saveStatusAuditTriageDetails(caseHearingRequestEntity.getHmctsServiceCode(),
                                                               hearingEntity.getId().toString(),
                               hearingEntity.getStatus(), hearingEntity.getUpdatedDateTime(),
                               "delete-hearing-request", "TODO", "hmc",null,
                               saveHearingResponseDetails.getVersionNumber().toString());
        String authorization = retrieveAuthToken().getAccessToken();
        return hmiClient.requestHearing(BEARER + authorization, data);
    }

    @Override
    public HearingManagementInterfaceResponse amendHearingRequest(JsonNode data, String caseListingRequestId) {
        log.debug("AmendHearingRequest sent to FH : {}", data.toString());
        String authorization = retrieveAuthToken().getAccessToken();
        return hmiClient.amendHearing(caseListingRequestId, BEARER + authorization, data);
    }

    @Override
    public HearingManagementInterfaceResponse deleteHearingRequest(JsonNode data, String caseListingRequestId) {
        log.debug("DeleteHearingRequest sent to FH : {}", data.toString());
        String authorization = retrieveAuthToken().getAccessToken();
        return hmiClient.deleteHearing(caseListingRequestId, BEARER + authorization, data);
    }

}

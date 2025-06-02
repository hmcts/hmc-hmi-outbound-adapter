package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationRequest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditService;

import java.util.Optional;

import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_AUTH_REQUEST;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI_TO_HMC_AUTH_RESPONSE;

@Slf4j
@Repository("defaultFutureHearingRepository")
public class DefaultFutureHearingRepository implements FutureHearingRepository {

    private final HearingManagementInterfaceApiClient hmiClient;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final HearingRepository hearingRepository;
    private final HearingStatusAuditService hearingStatusAuditService;
    private final ApplicationParams applicationParams;
    private static final String BEARER = "Bearer ";

    public DefaultFutureHearingRepository(ActiveDirectoryApiClient activeDirectoryApiClient,
                                          ApplicationParams applicationParams,
                                          HearingManagementInterfaceApiClient hmiClient,
                                          HearingRepository hearingRepository,
                                          HearingStatusAuditService hearingStatusAuditService) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
        this.hearingRepository = hearingRepository;
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
    public HearingManagementInterfaceResponse createHearingRequest(JsonNode data, String caseListingRequestId) {
        log.debug("In createHearingRequest process: {}", data.toString());
        String authorization = retrieveAuthToken().getAccessToken();
        HearingEntity hearingEntity = getHearingEntity(caseListingRequestId).get();
        saveAuditDetails(hearingEntity, HMC_TO_HMI_AUTH_REQUEST, null, HMC, HMI);

        log.debug("CreateHearingRequest sent to FH : {}", data.toString());
        HearingManagementInterfaceResponse response = hmiClient.requestHearing(BEARER + authorization, data);
        saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_RESPONSE, response.getResponseCode().toString(), HMI, HMC);
        log.debug("Received response for CreateHearingRequest from FH with responseCode: {},description: {}",
                  response.getResponseCode(),response.getDescription());
        return response;
    }

    @Override
    public HearingManagementInterfaceResponse amendHearingRequest(JsonNode data, String caseListingRequestId) {
        log.debug("In AmendHearingRequest process: {}", data.toString());
        String authorization = retrieveAuthToken().getAccessToken();
        HearingEntity hearingEntity = getHearingEntity(caseListingRequestId).get();
        saveAuditDetails(hearingEntity, HMC_TO_HMI_AUTH_REQUEST, null, HMC, HMI);

        log.debug("AmendHearingRequest sent to FH : {}", data.toString());
        HearingManagementInterfaceResponse response = hmiClient
            .amendHearing(caseListingRequestId, BEARER + authorization, data);

        saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_RESPONSE, response.getResponseCode().toString(), HMI, HMC);
        log.debug("Received response for amendHearingRequest from FH with responseCode: {},description: {}",
                  response.getResponseCode(),response.getDescription());
        return response;
    }

    @Override
    public HearingManagementInterfaceResponse deleteHearingRequest(JsonNode data, String caseListingRequestId) {
        log.debug("In deleteHearingRequest process: {}", data.toString());
        String authorization = retrieveAuthToken().getAccessToken();
        HearingEntity hearingEntity = getHearingEntity(caseListingRequestId).get();
        saveAuditDetails(hearingEntity, HMC_TO_HMI_AUTH_REQUEST, null, HMC, HMI);

        log.debug("DeleteHearingRequest sent to FH : {}", data.toString());
        HearingManagementInterfaceResponse response = hmiClient
            .deleteHearing(caseListingRequestId, BEARER + authorization, data);

        saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_RESPONSE, response.getResponseCode().toString(), HMI, HMC);
        log.debug("Received response for deleteHearingRequest from FH with responseCode: {},description: {}",
                  response.getResponseCode(),response.getDescription());
        return response;
    }

    private void saveAuditDetails(HearingEntity hearingEntity, String action, String responseCode,
                                  String source, String target) {
        hearingStatusAuditService.saveAuditTriageDetailsWithUpdatedDate(hearingEntity, action, responseCode,
                                                                        source, target, null);
    }

    @NotNull
    private Optional<HearingEntity> getHearingEntity(String caseListingRequestId) {
        Optional<HearingEntity> hearingEntity = hearingRepository.findById(Long.valueOf(caseListingRequestId));
        return hearingEntity;
    }
}

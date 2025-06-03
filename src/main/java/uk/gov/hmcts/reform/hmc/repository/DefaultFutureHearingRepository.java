package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public DefaultFutureHearingRepository(ActiveDirectoryApiClient activeDirectoryApiClient,
                                          ApplicationParams applicationParams,
                                          HearingManagementInterfaceApiClient hmiClient,
                                          HearingRepository hearingRepository,
                                          HearingStatusAuditService hearingStatusAuditService,
                                          ObjectMapper objectMapper) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
        this.hearingRepository = hearingRepository;
        this.hearingStatusAuditService = hearingStatusAuditService;
        this.objectMapper = objectMapper;
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
        return processHearingRequest(
            data, caseListingRequestId, "createHearingRequest",
            (authorization, requestData) -> hmiClient.requestHearing(
                BEARER + authorization, requestData));
    }

    @Override
    public HearingManagementInterfaceResponse amendHearingRequest(JsonNode data, String caseListingRequestId) {
        return processHearingRequest(
            data, caseListingRequestId, "amendHearingRequest",
            (authorization, requestData) -> hmiClient.amendHearing(
                caseListingRequestId, BEARER + authorization, requestData));
    }

    @Override
    public HearingManagementInterfaceResponse deleteHearingRequest(JsonNode data, String caseListingRequestId) {
        return processHearingRequest(
            data, caseListingRequestId, "deleteHearingRequest",
            (authorization, requestData) -> hmiClient.deleteHearing(
                caseListingRequestId, BEARER + authorization, requestData));
    }

    private HearingManagementInterfaceResponse processHearingRequest(JsonNode data, String caseListingRequestId,
                                                                     String operation,
                                                                     HearingRequestProcessor processor) {
        log.debug("In {} process: {}", operation, data.toString());
        String authorization = retrieveAuthToken().getAccessToken();
        Optional<HearingEntity> hearingEntityOpt = getHearingEntity(caseListingRequestId);

        HearingEntity hearingEntity = hearingEntityOpt.get();
        saveAuditDetails(hearingEntity, HMC_TO_HMI_AUTH_REQUEST, null, HMC, HMI, null);

        log.debug("{} sent to FH: {}", operation, data.toString());
        HearingManagementInterfaceResponse response = processor.process(authorization, data);

        JsonNode errorDescription = response.getResponseCode() != 200
            ? objectMapper.convertValue(response.getDescription(), JsonNode.class) : null;

        saveAuditDetails(
            hearingEntity, HMI_TO_HMC_AUTH_RESPONSE, response.getResponseCode().toString(), HMI, HMC, errorDescription);

        log.debug(
            "Received response for {} from FH with responseCode: {}, description: {}", operation,
            response.getResponseCode(), response.getDescription());
        return response;
    }

    private void saveAuditDetails(HearingEntity hearingEntity, String action, String responseCode,
                                  String source, String target, JsonNode errorDescription) {
        hearingStatusAuditService.saveAuditTriageDetailsWithUpdatedDate(hearingEntity, action, responseCode,
                                                                        source, target, errorDescription);
    }

    @NotNull
    private Optional<HearingEntity> getHearingEntity(String caseListingRequestId) {
        return hearingRepository.findById(Long.valueOf(caseListingRequestId));
    }

    @FunctionalInterface
    private interface HearingRequestProcessor {
        HearingManagementInterfaceResponse process(String authorization, JsonNode data);
    }
}

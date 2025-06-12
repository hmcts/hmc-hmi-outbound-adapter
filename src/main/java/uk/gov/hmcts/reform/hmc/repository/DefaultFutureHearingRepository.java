package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpStatus;
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
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI_TO_HMC_AUTH_FAIL;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI_TO_HMC_AUTH_SUCCESS;

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
        Optional<HearingEntity> hearingEntityOpt = getHearingEntity(caseListingRequestId);
        HearingEntity hearingEntity = hearingEntityOpt.get();
        String authorization = getAuthToken(caseListingRequestId, operation, hearingEntity);
        log.debug("{} sending to FH: {}", operation, data.toString());
        return processor.process(authorization, data);
    }

    private String getAuthToken(String caseListingRequestId, String operation, HearingEntity hearingEntity) {
        String authorization;
        try {
            log.debug("Retrieving authorization token for operation: {} hearingId: {}", operation,
                      caseListingRequestId);
            saveAuditDetails(hearingEntity, HMC_TO_HMI_AUTH_REQUEST, null, HMC, HMI, null);
            authorization = retrieveAuthToken().getAccessToken();
            log.debug("Authorization token retrieved successfully for operation: {} hearingId: {}", operation,
                      caseListingRequestId);
            saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_SUCCESS, String.valueOf(HttpStatus.OK_200),
                                HMI, HMC, null);
        } catch (Exception ex) {
            log.error("Failed to retrieve authorization token for hearingId: {} with exception {}",
                      caseListingRequestId, ex.getMessage());
            JsonNode errorDescription = objectMapper.convertValue(ex.getMessage(), JsonNode.class);
            saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_FAIL, String.valueOf(HttpStatus.UNAUTHORIZED_401),
                             HMI, HMC, errorDescription);
            throw new IllegalArgumentException("Failed to retrieve authorization token for operation: " + operation
                                                   + " hearingId: " + caseListingRequestId, ex);
        }
        return authorization;
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

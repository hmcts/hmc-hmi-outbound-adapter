package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RetryableException;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationRequest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HealthCheckResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckActiveDirectoryException;
import uk.gov.hmcts.reform.hmc.errorhandling.HealthCheckHmiException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditService;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
    public HealthCheckResponse privateHealthCheck() {
        String authorization;

        try {
            log.debug("Retrieving authorization token for HMI private health check");
            authorization = retrieveAuthToken().getAccessToken();
            log.debug("Authorization token retrieved successfully for HMI private health check");
        } catch (BadFutureHearingRequestException e) {
            logDebugHealthCheckActiveDirectoryException(e.getClass().getSimpleName());
            throw createHealthCheckActiveDirectoryException(e);
        } catch (AuthenticationException e) {
            logDebugHealthCheckActiveDirectoryException(e.getClass().getSimpleName());
            throw createHealthCheckActiveDirectoryException(e);
        } catch (ResourceNotFoundException e) {
            logDebugHealthCheckActiveDirectoryException(e.getClass().getSimpleName());
            throw new HealthCheckActiveDirectoryException("Resource not found");
        } catch (RetryableException e) {
            log.error(e.getMessage());
            logDebugHealthCheckActiveDirectoryException(e);
            throw new HealthCheckActiveDirectoryException("Connection/Read timeout");
        }

        return getPrivateHealthCheck(authorization);
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
            saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_SUCCESS, String.valueOf(HttpStatus.OK.value()),
                                HMI, HMC, null);
        } catch (Exception ex) {
            log.error("Failed to retrieve authorization token for hearingId: {} with exception {}",
                      caseListingRequestId, ex.getMessage());
            JsonNode errorDescription = objectMapper.convertValue(ex.getMessage(), JsonNode.class);
            saveAuditDetails(hearingEntity, HMI_TO_HMC_AUTH_FAIL, String.valueOf(HttpStatus.UNAUTHORIZED.value()),
                             HMI, HMC, errorDescription);
            throw new AuthenticationException("Failed to retrieve authorization token for operation: " + operation
                                              + " hearingId: " + caseListingRequestId);
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

    private HealthCheckResponse getPrivateHealthCheck(String authorization) {
        try {
            log.debug("Calling HMI private health check");
            return hmiClient.privateHealthCheck(BEARER + authorization);
        } catch (BadFutureHearingRequestException e) {
            logDebugHealthCheckHmiException(e);
            throw createHealthCheckHmiException(e);
        } catch (AuthenticationException e) {
            logDebugHealthCheckHmiException(e);
            throw createHealthCheckHmiException(e);
        } catch (ResourceNotFoundException e) {
            logDebugHealthCheckHmiException(e);
            throw new HealthCheckHmiException("Resource not found");
        }
    }

    private void logDebugHealthCheckActiveDirectoryException(RetryableException retryableException) {
        Request request = retryableException.request();
        String requestBody = request.body() == null ? "N/A" : new String(request.body(), StandardCharsets.UTF_8);
        log.debug("Request to Active Directory timed out - "
                      + "URL: {}, Method: {}, Body: {}", request.url(), request.httpMethod(), requestBody);

        logDebugHealthCheckActiveDirectoryException(retryableException.getClass().getSimpleName());
    }

    private void logDebugHealthCheckActiveDirectoryException(String exceptionClassName) {
        log.debug("Failed to get authorization token for HMI health check. "
                      + "Converting {} exception to HealthCheckActiveDirectoryException.", exceptionClassName);
    }

    private void logDebugHealthCheckHmiException(Exception e) {
        log.debug("HMI health check failed. Converting {} exception to HealthCheckHmiException.",
                  e.getClass().getSimpleName());
    }

    private HealthCheckActiveDirectoryException createHealthCheckActiveDirectoryException(
        BadFutureHearingRequestException exception) {
        HealthCheckActiveDirectoryException healthCheckActiveDirectoryException;

        ErrorDetails errorDetails = exception.getErrorDetails();
        if (errorDetails == null) {
            healthCheckActiveDirectoryException = new HealthCheckActiveDirectoryException(exception.getMessage());
        } else {
            Integer authErrorCode = getAuthErrorCode(errorDetails.getAuthErrorCodes());
            healthCheckActiveDirectoryException =
                new HealthCheckActiveDirectoryException(exception.getMessage(),
                                                        authErrorCode,
                                                        errorDetails.getAuthErrorDescription());
        }

        return healthCheckActiveDirectoryException;
    }

    private HealthCheckActiveDirectoryException createHealthCheckActiveDirectoryException(
        AuthenticationException exception) {
        HealthCheckActiveDirectoryException healthCheckActiveDirectoryException;

        ErrorDetails errorDetails = exception.getErrorDetails();
        if (errorDetails == null) {
            healthCheckActiveDirectoryException = new HealthCheckActiveDirectoryException(exception.getMessage());
        } else {
            Integer authErrorCode = getAuthErrorCode(errorDetails.getAuthErrorCodes());
            healthCheckActiveDirectoryException =
                new HealthCheckActiveDirectoryException(exception.getMessage(),
                                                        authErrorCode,
                                                        errorDetails.getAuthErrorDescription());
        }

        return healthCheckActiveDirectoryException;
    }

    private HealthCheckHmiException createHealthCheckHmiException(BadFutureHearingRequestException exception) {
        HealthCheckHmiException healthCheckHmiException;

        ErrorDetails errorDetails = exception.getErrorDetails();
        if (errorDetails == null) {
            healthCheckHmiException = new HealthCheckHmiException(exception.getMessage());
        } else {
            healthCheckHmiException = new HealthCheckHmiException(exception.getMessage(),
                                                                  errorDetails.getApiStatusCode(),
                                                                  errorDetails.getApiErrorMessage());
        }

        return healthCheckHmiException;
    }

    private HealthCheckHmiException createHealthCheckHmiException(AuthenticationException exception) {
        HealthCheckHmiException healthCheckHmiException;

        ErrorDetails errorDetails = exception.getErrorDetails();
        if (errorDetails == null) {
            healthCheckHmiException = new HealthCheckHmiException(exception.getMessage());
        } else {
            healthCheckHmiException = new HealthCheckHmiException(exception.getMessage(),
                                                                  errorDetails.getApiStatusCode(),
                                                                  errorDetails.getApiErrorMessage());
        }

        return healthCheckHmiException;
    }

    private Integer getAuthErrorCode(List<Integer> authErrorCodes) {
        return authErrorCodes != null && !authErrorCodes.isEmpty() ? authErrorCodes.getFirst() : null;
    }
}

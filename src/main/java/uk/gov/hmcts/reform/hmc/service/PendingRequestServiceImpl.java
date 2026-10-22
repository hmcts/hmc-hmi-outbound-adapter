package uk.gov.hmcts.reform.hmc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.config.MessageSenderToTopicConfiguration;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingResponseEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.ApiClientException;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServerErrorException;
import uk.gov.hmcts.reform.hmc.helper.hmi.HmiHearingResponseMapper;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;
import uk.gov.hmcts.reform.hmc.model.HmcHearingResponse;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.EXCEPTION;
import static uk.gov.hmcts.reform.hmc.constants.Constants.EXCEPTION_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.FH;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.LA_FAILURE_STATUS;
import static uk.gov.hmcts.reform.hmc.constants.Constants.LA_RESPONSE;

@Slf4j
@Service
public class PendingRequestServiceImpl implements PendingRequestService {

    private final HearingRepository hearingRepository;
    @Value("${pending.request.escalation-wait-interval:1,DAY}")
    public String escalationWaitInterval;

    @Value("${pending.request.deletion-wait-interval:30,DAYS}")
    public String deletionWaitInterval;

    @Value("${pending.request.exception-limit-in-hours:4}")
    public Long exceptionLimitInHours;

    @Value("${pending.request.retry-limit-in-minutes:20}")
    public Long retryLimitInMinutes;

    @Value("${pending.request.claim-limit-in-minutes:30}")
    public Long claimLimitInMinutes;

    private final HearingStatusAuditService hearingStatusAuditService;
    private final ObjectMapper objectMapper;
    private final PendingRequestRepository pendingRequestRepository;
    private final MessageSenderToTopicConfiguration messageSenderToTopicConfiguration;
    private final HmiHearingResponseMapper hmiHearingResponseMapper;

    public PendingRequestServiceImpl(ObjectMapper objectMapper,
                                     PendingRequestRepository pendingRequestRepository,
                                     HearingRepository hearingRepository,
                                     HearingStatusAuditService hearingStatusAuditService,
                                     MessageSenderToTopicConfiguration messageSenderToTopicConfiguration,
                                     HmiHearingResponseMapper hmiHearingResponseMapper) {
        this.objectMapper = objectMapper;
        this.pendingRequestRepository = pendingRequestRepository;
        this.hearingRepository = hearingRepository;
        this.hearingStatusAuditService = hearingStatusAuditService;
        this.messageSenderToTopicConfiguration = messageSenderToTopicConfiguration;
        this.hmiHearingResponseMapper = hmiHearingResponseMapper;
    }

    @Transactional
    public PendingRequestEntity claimNextPendingRequest() {
        UUID claimToken = UUID.randomUUID();

        return pendingRequestRepository.claimNextPendingRequest(
            retryLimitInMinutes,
            claimToken
        );
    }

    public void resetFailedClaimedRequest(Long id, UUID claimToken) {
        log.debug("resetFailedClaimedRequest({}, {})", id, claimToken);
        pendingRequestRepository.resetFailedClaimedRequest(id, claimToken);
        log.debug("resetFailedClaimedRequest({}, {})", id, claimToken);
    }

    public void completeClaimedRequest(Long id, UUID claimToken) {
        log.info("completeClaimedRequest({}, {})", id, claimToken);
        pendingRequestRepository.completeClaimedRequest(id, claimToken);
        log.debug("completeClaimedRequest({}, {} completed)", id, claimToken);
    }

    @Override
    public void markOverduePendingRequestsAsException() {
        LocalDateTime exceptionLimitTime = LocalDateTime.now().minusHours(exceptionLimitInHours);
        int count = pendingRequestRepository.markOverduePendingRequestsAsException(exceptionLimitTime);
        log.info("Marked {} overdue pending requests as EXCEPTION", count);
    }

    @Override
    public void resetTimedOutClaimedRequests() {
        LocalDateTime claimLimitTime = LocalDateTime.now().minusMinutes(claimLimitInMinutes);
        int count = pendingRequestRepository.resetTimedOutClaimedRequests(claimLimitTime);
        log.info("Reset {} timed out claimed requests", count);
    }

    @Override
    public void handleNonRetriableException(PendingRequestEntity pendingRequest, Exception exception, UUID claimToken) {
        Long hearingId = pendingRequest.getHearingId();

        Optional<HearingEntity> hearingEntityOptional = hearingRepository.findById(hearingId);
        if (hearingEntityOptional.isPresent()) {
            HearingEntity hearing = hearingEntityOptional.get();
            catchExceptionAndUpdateHearing(hearing, exception);
            pendingRequestRepository.markClaimedRequestAsExceptionWithIncident(pendingRequest.getId(), claimToken);
        } else {
            log.error("Hearing id {} not found", hearingId);
            pendingRequestRepository.markClaimedRequestAsException(pendingRequest.getId(), claimToken);
        }
    }

    public void catchExceptionAndUpdateHearing(HearingEntity hearingEntity, Exception exception) {
        Long hearingId = hearingEntity.getId();
        log.debug("catchExceptionAndUpdateHearing ({}, {})", hearingId, exception.getMessage());

        hearingEntity.setStatus(EXCEPTION.name());
        hearingEntity.setUpdatedDateTime(LocalDateTime.now());

        JsonNode errorDetails = null;
        BiConsumer<Exception, HearingEntity> handler = EXCEPTION_HANDLERS.get(exception.getClass());
        if (handler != null) {
            handler.accept(exception, hearingEntity);
            errorDetails = extractErrorDetails(exception);
        } else {
            log.error("Unhandled exception type for hearing id {}, exception {}, errorMessage {}", hearingId,
                      exception.getClass(), exception.getMessage());
        }
        hearingRepository.save(hearingEntity);
        HmcHearingResponse hmcHearingResponse = getHmcHearingResponse(hearingEntity);
        log.debug("Sending hearing id {} to topic with Hearing response {}", hearingId, hmcHearingResponse);
        messageSenderToTopicConfiguration
            .sendMessage(objectMapper.convertValue(hmcHearingResponse, JsonNode.class).toString(),
                         hmcHearingResponse.getHmctsServiceCode(), hearingId.toString(),
                         hearingEntity.getDeploymentId());
        logErrorStatusToException(hearingId, hearingEntity.getLatestCaseReferenceNumber(),
                                  hearingEntity.getLatestCaseHearingRequest().getHmctsServiceCode(),
                                  hearingEntity.getErrorDescription());
        JsonNode errorInfo = objectMapper.convertValue(errorDetails, JsonNode.class);
        HearingStatusAuditContext hearingStatusAuditContext =
            HearingStatusAuditContext.builder()
                .hearingEntity(hearingEntity)
                .hearingEvent(LA_RESPONSE)
                .httpStatus(LA_FAILURE_STATUS)
                .source(FH)
                .target(HMC)
                .errorDetails(errorInfo)
                .build();
        hearingStatusAuditService.saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(hearingStatusAuditContext);
    }

    public Optional<PendingRequestEntity> findById(Long pendingRequestId) {
        return pendingRequestRepository.findById(pendingRequestId);
    }

    private static final Map<Class<? extends Exception>, BiConsumer<Exception, HearingEntity>> EXCEPTION_HANDLERS =
        Map.of(
            ResourceNotFoundException.class, (ex, entity) ->
                handleResourceNotFoundException((ResourceNotFoundException) ex, entity),
            AuthenticationException.class, (ex, entity) ->
                handleAuthenticationException((AuthenticationException) ex, entity),
            BadFutureHearingRequestException.class, (ex, entity) ->
                handleBadFutureHearingRequestException((BadFutureHearingRequestException) ex, entity),
            ApiClientException.class, (ex, entity) ->
                handleApiClientException((ApiClientException) ex, entity),
            ServerErrorException.class, (ex, entity) ->
                handleServerErrorException((ServerErrorException) ex, entity)
        );

    public void escalatePendingRequests() {
        log.info("escalatePendingRequests()");

        try {
            List<PendingRequestEntity> pendingRequests =
                pendingRequestRepository.findRequestsForEscalation(getIntervalUnits(escalationWaitInterval),
                                                                   getIntervalMeasure(escalationWaitInterval));
            pendingRequests.forEach(this::escalatePendingRequest);
        } catch (Exception e) {
            log.error("Failed to escalate Pending Requests");
        }

    }

    public void deleteCompletedRequests() {
        log.info("deleteCompletedPendingRequests({})", deletionWaitInterval);
        try {
            int countOfDeletedRecords = pendingRequestRepository.deleteCompletedRequests(
                getIntervalUnits(deletionWaitInterval), getIntervalMeasure(deletionWaitInterval));
            log.debug("{} Completed pendingRequests deleted", countOfDeletedRecords);
        } catch (Exception e) {
            log.error("Failed to deleteCompletedRecords");
        }
    }

    protected void escalatePendingRequest(PendingRequestEntity pendingRequest) {
        log.info("escalatePendingRequests");
        pendingRequestRepository.markRequestForEscalation(pendingRequest.getId(), LocalDateTime.now());
        HearingEntity hearingEntity = hearingRepository.findById(pendingRequest.getHearingId()).get();
        logErrorStatusToException(hearingEntity.getId(), hearingEntity.getLatestCaseReferenceNumber(),
                                 hearingEntity.getLatestCaseHearingRequest().getHmctsServiceCode(),
                                 hearingEntity.getErrorDescription());
    }

    protected Long getIntervalUnits(String envVarInterval) {
        return Long.valueOf(envVarInterval.split(",")[0]);
    }

    protected String getIntervalMeasure(String envVarInterval) {
        return envVarInterval.split(",")[1];
    }

    private static void logErrorStatusToException(Long hearingId, String caseRef, String serviceCode,
                                           String errorDescription) {
        log.error(EXCEPTION_MESSAGE, hearingId, caseRef, serviceCode, errorDescription, EXCEPTION.name());
    }

    private static void handleResourceNotFoundException(ResourceNotFoundException ex, HearingEntity entity) {
        handleException(entity, HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    private static void handleAuthenticationException(AuthenticationException ex, HearingEntity entity) {
        ErrorDetails errorDetails = ex.getErrorDetails();
        Integer errorCode = (errorDetails != null
            && errorDetails.getAuthErrorCodes() != null
            && !errorDetails.getAuthErrorCodes().isEmpty())
            ? errorDetails.getAuthErrorCodes().getFirst() : HttpStatus.UNAUTHORIZED.value();
        String errorDescription = errorDetails != null ? errorDetails.getAuthErrorDescription() : null;
        handleException(entity, errorCode, errorDescription);
    }

    private static void handleBadFutureHearingRequestException(BadFutureHearingRequestException ex,
                                                               HearingEntity entity) {
        handleException(entity, ex.getErrorDetails().getErrorCode(), ex.getErrorDetails().getErrorDescription());
    }

    private static void handleApiClientException(ApiClientException ex, HearingEntity entity) {
        handleException(entity, ex.getErrorCode(), ex.getErrorDescription());
    }

    private static void handleServerErrorException(ServerErrorException ex, HearingEntity entity) {
        handleException(entity, ex.deriveErrorCode(), ex.deriveErrorMessage());
    }

    private JsonNode extractErrorDetails(Exception exception) {
        if (exception instanceof ResourceNotFoundException resourceNotFoundException) {
            return objectMapper.convertValue(resourceNotFoundException.getMessage(), JsonNode.class);
        } else if (exception instanceof AuthenticationException authException) {
            return objectMapper.convertValue(authException.getErrorDetails(), JsonNode.class);
        } else if (exception instanceof BadFutureHearingRequestException badRequestException) {
            return objectMapper.convertValue(badRequestException.getErrorDetails(), JsonNode.class);
        } else if (exception instanceof ApiClientException apiClientException) {
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("errorCode", apiClientException.getErrorCode());
            errorInfo.put("errorDescription", apiClientException.getErrorDescription());
            return objectMapper.convertValue(errorInfo, JsonNode.class);
        } else if (exception instanceof ServerErrorException serverErrorException) {
            return objectMapper.convertValue(serverErrorException.getErrorDetails(), JsonNode.class);
        }
        return objectMapper.convertValue(exception.getMessage(), JsonNode.class);
    }

    private static void handleException(HearingEntity entity, Integer errorCode,
                                        String errorDescription) {
        entity.setErrorCode(errorCode);
        entity.setErrorDescription(errorDescription);
    }

    private HmcHearingResponse getHmcHearingResponse(HearingEntity hearingEntity) {
        Optional<HearingResponseEntity> hearingResponseEntity = hearingEntity.getLatestHearingResponse();
        return hearingResponseEntity.isPresent()
            ? hmiHearingResponseMapper.mapEntityToHmcModel(hearingResponseEntity.get(), hearingEntity)
            : hmiHearingResponseMapper.mapEntityToHmcModel(hearingEntity);
    }

}

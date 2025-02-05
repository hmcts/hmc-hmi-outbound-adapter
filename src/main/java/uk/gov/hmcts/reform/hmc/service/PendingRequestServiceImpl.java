package uk.gov.hmcts.reform.hmc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class PendingRequestServiceImpl implements PendingRequestService {

    @Value("${pending.request.pending-wait-interval:15,MINUTES}")
    public String pendingWaitInterval;

    @Value("${pending.request.escalation-wait-interval:1,DAY}")
    public String escalationWaitInterval;

    @Value("${pending.request.deletion-wait-interval:30,DAYS}")
    public String deletionWaitInterval;

    @Value("${pending.request.exception-limit-in-hours:4}")
    public Long exceptionLimitInHours;

    @Value("${pending.request.retry-limit-in-minutes:20}")
    public Long retryLimitInMinutes;

    private final PendingRequestRepository pendingRequestRepository;

    public PendingRequestServiceImpl(PendingRequestRepository pendingRequestRepository) {
        this.pendingRequestRepository = pendingRequestRepository;
    }

    public boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        LocalDateTime submittedDateTime = pendingRequest.getSubmittedDateTime();
        long hoursElapsed = ChronoUnit.HOURS.between(submittedDateTime, currentDateTime);
        log.debug("Hours elapsed = {}; submittedDateTime: {}; currentDateTime: {}",
                  hoursElapsed, submittedDateTime, currentDateTime);
        boolean result = false;
        if (hoursElapsed >= exceptionLimitInHours) {
            log.debug("Marking hearing request {} as Exception (hours elapsed exceeds limit!)",
                      pendingRequest.getHearingId());
            markRequestWithGivenStatus(pendingRequest.getId(), PendingStatusType.EXCEPTION.name());
            log.error("Submitted time of request with ID {} is {} hours later than before.",
                      pendingRequest.getHearingId(), exceptionLimitInHours);
            result = true;
        }
        log.debug("submittedDateTimePeriodElapsed()={} hearingId<{}>", result, pendingRequest.getHearingId());
        return result;
    }

    public boolean lastTriedDateTimePeriodElapsed(PendingRequestEntity pendingRequest) {
        LocalDateTime lastTriedDateTime = pendingRequest.getLastTriedDateTime();
        if (lastTriedDateTime == null) {
            return true;
        }

        long minutesElapsed = ChronoUnit.MINUTES.between(lastTriedDateTime, LocalDateTime.now());
        boolean result = retryLimitInMinutes < minutesElapsed;
        log.debug("lastTriedDateTimePeriodNotElapsed()={}  retryLimitInMinutes<{}> hearingId<{}> Minutes elapsed<{}> "
                      + "submittedDateTime<{}> currentDateTime<{}>",
                  result, retryLimitInMinutes, pendingRequest.getHearingId(), minutesElapsed, lastTriedDateTime,
                  LocalDateTime.now());
        return result;
    }

    public List<PendingRequestEntity> findAndLockByHearingId(Long hearingId) {
        List<PendingRequestEntity> lockedRequests =
            pendingRequestRepository.findAndLockByHearingId(hearingId);
        log.debug(
            "{} locked records = findAndLockByHearingId({})",
            null == lockedRequests ? 0 : lockedRequests.size(),
            hearingId
        );
        return lockedRequests;
    }

    public List<PendingRequestEntity> findQueuedPendingRequestsForProcessing() {
        List<PendingRequestEntity> pendingRequests =
            pendingRequestRepository.findQueuedPendingRequestsForProcessing(
                getIntervalUnits(pendingWaitInterval), getIntervalMeasure(pendingWaitInterval));
        if (!pendingRequests.isEmpty()) {
            pendingRequests.forEach(e ->
                log.debug("findQueuedPendingRequestsForProcessing(): id<{}> hearingId<{}> ",
                      e.getId(), e.getHearingId()));
        } else {
            log.debug("findQueuedPendingRequestsForProcessing(): empty");
        }
        return pendingRequests;
    }

    public void markRequestAsPending(Long id, Integer retryCountIn, LocalDateTime lastTriedDateTimeIn) {
        log.debug("markRequestAsPending({}, {}, {})", id, retryCountIn, lastTriedDateTimeIn);
        int retryCountOut = retryCountIn + 1;
        LocalDateTime lastTriedDateTimeOut = LocalDateTime.now();
        pendingRequestRepository.markRequestAsPending(id, retryCountOut, lastTriedDateTimeOut);
        log.debug("markRequestAsPending({}, {}, {})", id, retryCountOut, lastTriedDateTimeOut);
    }

    public void markRequestWithGivenStatus(Long id, String status) {
        log.debug("markRequestWithGivenStatus({}, {})", id, status);
        pendingRequestRepository.markRequestWithGivenStatus(id, status);
    }

    public void escalatePendingRequests() {
        log.debug("escalatePendingRequests()");

        try {
            List<PendingRequestEntity> pendingRequests =
                pendingRequestRepository.findRequestsForEscalation(getIntervalUnits(escalationWaitInterval),
                                                                   getIntervalMeasure(escalationWaitInterval));
            pendingRequests.forEach(this::escalatePendingRequest);
        } catch (Exception e) {
            log.error("Failed to escalate Pending Requests");
        }

    }

    public void deleteCompletedPendingRequests() {
        log.debug("deleteCompletedPendingRequests({})", deletionWaitInterval);
        try {
            int countOfDeletedRecords = pendingRequestRepository.deleteCompletedRecords(
                getIntervalUnits(deletionWaitInterval), getIntervalMeasure(deletionWaitInterval));
            log.debug("{} Completed pendingRequests deleted", countOfDeletedRecords);
        } catch (Exception e) {
            log.error("Failed to deleteCompletedRecords");
        }
    }

    protected void escalatePendingRequest(PendingRequestEntity pendingRequest) {
        log.debug("escalatePendingRequests");
        pendingRequestRepository.markRequestForEscalation(pendingRequest.getId(), LocalDateTime.now());

        log.error("Error occurred during service bus processing. Service:{}. Entity:{}. Method:{}. Hearing ID:{}.",
                  "MessageProcessor", pendingRequest, "escalatePendingRequest", pendingRequest.getHearingId());
    }

    protected Long getIntervalUnits(String envVarInterval) {
        return Long.valueOf(envVarInterval.split(",")[0]);
    }

    protected String getIntervalMeasure(String envVarInterval) {
        return envVarInterval.split(",")[1];
    }

}

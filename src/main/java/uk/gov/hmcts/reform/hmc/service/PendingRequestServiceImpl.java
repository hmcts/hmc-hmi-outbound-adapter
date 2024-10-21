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

    @Value("${pending.request.pending.request.retry-limit-in-minutes:20}")
    public Long retryLimitInMinutes;

    private final PendingRequestRepository pendingRequestRepository;

    public PendingRequestServiceImpl(PendingRequestRepository pendingRequestRepository) {
        this.pendingRequestRepository = pendingRequestRepository;
    }

    public boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest) {
        log.debug("submittedDateTimePeriodElapsed() hearingId<{}>", pendingRequest.getHearingId());
        LocalDateTime currentDateTime = LocalDateTime.now();
        LocalDateTime submittedDateTime = pendingRequest.getSubmittedDateTime().toLocalDateTime();
        long hoursElapsed = ChronoUnit.HOURS.between(submittedDateTime, currentDateTime);
        log.debug("Hours elapsed = {}; submittedDateTime: {}; currentDateTime: {}",
                  hoursElapsed, submittedDateTime, currentDateTime);
        if (hoursElapsed >= exceptionLimitInHours) {
            log.debug("Marking hearing request {} as Exception (hours elapsed exceeds limit!)",
                      pendingRequest.getHearingId());
            markRequestWithGivenStatus(pendingRequest.getId(), PendingStatusType.EXCEPTION.name());
            log.error("Submitted time of request with ID {} is {} hours later than before.",
                      pendingRequest.getHearingId(), exceptionLimitInHours);
            identifyPendingRequestsForEscalation();
            return true;
        }
        return false;
    }

    public boolean lastTriedDateTimePeriodNotElapsed(PendingRequestEntity pendingRequest) {
        log.debug("lastTriedDateTimePeriodNotElapsed() hearingId<{}>", pendingRequest.getHearingId());
        LocalDateTime currentDateTime = LocalDateTime.now();
        LocalDateTime lastTriedDateTime = pendingRequest.getLastTriedDateTime().toLocalDateTime();
        long minutesElapsed = ChronoUnit.MINUTES.between(lastTriedDateTime, currentDateTime);
        log.debug("Minutes elapsed = {}; submittedDateTime: {}; currentDateTime: {}",
                  minutesElapsed, lastTriedDateTime, currentDateTime);
        return (minutesElapsed < retryLimitInMinutes);
    }

    public PendingRequestEntity findById(Long id) {
        log.debug("findById({})", id);
        return pendingRequestRepository.findById(id).orElse(null);
    }

    public List<PendingRequestEntity> findAndLockByHearingId(Long hearingId) {
        log.debug("findAndLockByHearingId({})", hearingId);
        return pendingRequestRepository.findAndLockByHearingId(hearingId);
    }

    public PendingRequestEntity findOldestPendingRequestForProcessing() {
        PendingRequestEntity pendingRequest =
            pendingRequestRepository.findOldestPendingRequestForProcessing(
                getIntervalUnits(pendingWaitInterval), getIntervalMeasure(pendingWaitInterval));
        if (null != pendingRequest) {
            log.debug("findOldestPendingRequestForProcessing(): id<{}> hearingId<{}> ",
                      pendingRequest.getId(), pendingRequest.getHearingId());
        } else {
            log.debug("findOldestPendingRequestForProcessing(): null");
        }
        return pendingRequest;
    }

    public void markRequestAsPending(Long id, Integer retryCount) {
        log.debug("markRequestAsPending({}, {})", id, retryCount);
        pendingRequestRepository.markRequestAsPending(id, retryCount + 1);
    }

    public void markRequestWithGivenStatus(Long id, String status) {
        log.debug("markRequestWithGivenStatus({}, {})", id, status);
        pendingRequestRepository.markRequestWithGivenStatus(id, status);

        if (log.isDebugEnabled()) {
            PendingRequestEntity pendingRequest = findById(id);
            log.debug("pendingRequest = <{}>", pendingRequest);
        }
    }

    public void identifyPendingRequestsForEscalation() {
        log.debug("identifyPendingRequests for Escalation({})", escalationWaitInterval);
        pendingRequestRepository.identifyRequestsForEscalation(
            getIntervalUnits(escalationWaitInterval), getIntervalMeasure(escalationWaitInterval));
    }

    public void deleteCompletedPendingRequests() {
        log.debug("deleteCompletedPendingRequests({})", deletionWaitInterval);
        pendingRequestRepository.deleteCompletedRecords(
            getIntervalUnits(deletionWaitInterval), getIntervalMeasure(deletionWaitInterval));
    }

    protected Long getIntervalUnits(String envVarInterval) {
        return Long.valueOf(envVarInterval.split(",")[0]);

    }

    protected String getIntervalMeasure(String envVarInterval) {
        return envVarInterval.split(",")[1];

    }

}

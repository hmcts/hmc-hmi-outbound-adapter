package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface PendingRequestService {

    boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    boolean lastTriedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    List<PendingRequestEntity> findAndLockByHearingId(Long hearingId);

    List<PendingRequestEntity> findQueuedPendingRequestsForProcessing();

    void markRequestWithGivenStatus(Long id, String status);

    void markRequestAsPending(Long hearingId, Integer retryCount, LocalDateTime lastTriedDateTimeIn);

    void deleteCompletedPendingRequests();

    void escalatePendingRequests();

    void catchExceptionAndUpdateHearing(Long hearingId, Exception exception);
}

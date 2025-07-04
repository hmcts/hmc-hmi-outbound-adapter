package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PendingRequestService {

    boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    boolean lastTriedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    List<PendingRequestEntity> findAndLockByHearingId(Long hearingId);

    List<PendingRequestEntity> findQueuedPendingRequestsForProcessing();

    void markRequestWithGivenStatus(Long id, String status);

    int claimRequest(Long id);

    void markRequestAsPending(Long hearingId, Integer retryCount, LocalDateTime lastTriedDateTimeIn);

    void deleteCompletedPendingRequests();

    void escalatePendingRequests();

    void catchExceptionAndUpdateHearing(Long hearingId, Exception exception);

    Optional<PendingRequestEntity> findById(Long pendingRequestId);
}

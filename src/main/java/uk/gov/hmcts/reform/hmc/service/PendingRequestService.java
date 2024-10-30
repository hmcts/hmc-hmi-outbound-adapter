package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.List;

public interface PendingRequestService {

    boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    boolean lastTriedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    List<PendingRequestEntity> findAndLockByHearingId(Long hearingId);

    List<PendingRequestEntity> findQueuedPendingRequestsForProcessing();

    void markRequestWithGivenStatus(Long id, String status);

    void markRequestAsPending(Long hearingId, Integer retryCount);

    void deleteCompletedPendingRequests();

    void escalatePendingRequests();
}

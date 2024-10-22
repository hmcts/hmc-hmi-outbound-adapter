package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.List;

public interface PendingRequestService {

    boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    boolean lastTriedDateTimePeriodNotElapsed(PendingRequestEntity pendingRequest);

    List<PendingRequestEntity> findAndLockByHearingId(Long hearingId);

    PendingRequestEntity findOldestPendingRequestForProcessing();

    void markRequestWithGivenStatus(Long id, String status);

    void markRequestAsPending(Long hearingId, Integer retryCount);

    void identifyPendingRequestsForEscalation();

    void deleteCompletedPendingRequests();

}

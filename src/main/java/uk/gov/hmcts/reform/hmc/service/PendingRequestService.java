package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.List;

public interface PendingRequestService {

    PendingRequestEntity findById(Long id);

    boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest);

    boolean lastTriedDateTimePeriodNotElapsed(PendingRequestEntity pendingRequest);

    void addToPendingRequests(Object message);

    List<PendingRequestEntity> findAndLockByHearingId(Long hearingId);

    PendingRequestEntity findOldestPendingRequestForProcessing();

    void markRequestAsProcessing(Long hearingId);

    void markRequestAsPending(Long hearingId, Integer retryCount);

    void markRequestAsCompleted(Long hearingId);

    void markRequestAsException(Long hearingId);

    void identifyRequestsForEscalation();

    void deleteCompletedRecords();

}

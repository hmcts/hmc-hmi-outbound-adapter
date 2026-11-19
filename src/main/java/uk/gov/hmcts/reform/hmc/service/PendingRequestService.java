package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.Optional;
import java.util.UUID;

public interface PendingRequestService {

    PendingRequestEntity claimNextPendingRequest();

    void completeClaimedRequest(Long id, UUID claimToken);

    void resetFailedClaimedRequest(Long id, UUID claimToken);

    void deleteCompletedRequests();

    void escalatePendingRequests();

    void markOverduePendingRequestsAsException();

    void resetTimedOutClaimedRequests();

    void handleNonRetriableException(PendingRequestEntity pendingRequest, Exception exception, UUID claimToken);

    void catchExceptionAndUpdateHearing(HearingEntity hearingEntity, Exception exception);

    Optional<PendingRequestEntity> findById(Long pendingRequestId);
}

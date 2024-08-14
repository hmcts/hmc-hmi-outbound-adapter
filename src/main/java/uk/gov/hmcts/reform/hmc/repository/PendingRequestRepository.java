package uk.gov.hmcts.reform.hmc.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.List;

@Transactional(propagation = Propagation.REQUIRES_NEW)
@Repository("pendingRequestRepository")
public interface PendingRequestRepository extends CrudRepository<PendingRequestEntity, Long> {

    @Query(value = "SELECT * FROM public.pending_requests WHERE status = 'PENDING' "
        + "AND (last_tried_date_time IS NULL OR last_tried_date_time < NOW() - INTERVAL '15' MINUTE) "
        + "ORDER BY submitted_date_time ASC LIMIT 1", nativeQuery = true)
    PendingRequestEntity findOldestPendingRequestForProcessing();

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PROCESSING' WHERE pr.id = :id")
    void markRequestAsProcessing(Long id);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'COMPLETED' WHERE pr.id = :id")
    void markRequestAsCompleted(Long id);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'EXCEPTION' WHERE pr.id = :id")
    void markRequestAsException(Long id);

    @Query(value = "SELECT * FROM public.pending_requests WHERE submitted_date_time < NOW() - INTERVAL"
        + "'1 DAY' AND incident_flag = false", nativeQuery = true)
    List<PendingRequestEntity> findRequestsForEscalation();

    @Modifying
    @Query(value = "UPDATE public.pending_requests SET incident_flag = true WHERE submitted_date_time < NOW()"
        + " - INTERVAL '1 DAY' AND incident_flag = false", nativeQuery = true)
    void identifyRequestsForEscalation();

    @Modifying
    @Query(value = "DELETE FROM public.pending_requests WHERE status = 'COMPLETED' AND submitted_date_time < NOW()"
        + " - INTERVAL '30 DAYS'", nativeQuery = true)
    void deleteCompletedRecords();

    // @Modifying
    // @Query("DELETE FROM PendingRequestEntity pr WHERE pr.status = 'COMPLETED'
    // AND pr.submittedDateTime < :thresholdDateTime")
    // void deleteCompletedRecords(Timestamp thresholdDateTime);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = :status, pr.retryCount = :retryCount WHERE pr.id = :id")
    void updateStatusAndRetryCount(Long id, String status, int retryCount);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PENDING', pr.retryCount = :retryCount WHERE pr.id = :id")
    void markRequestAsPending(Long id, int retryCount);

}

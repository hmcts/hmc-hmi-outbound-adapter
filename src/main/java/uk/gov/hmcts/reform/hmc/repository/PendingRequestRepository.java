package uk.gov.hmcts.reform.hmc.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.sql.Timestamp;
import java.util.List;

@Transactional(propagation = Propagation.REQUIRES_NEW)
@Repository("pendingRequestRepository")
public interface PendingRequestRepository extends CrudRepository<PendingRequestEntity, Long> {

    @Query(value = "SELECT * FROM pending_requests WHERE status = 'PENDING' "
        + "AND (last_tried_date_time IS NULL OR last_tried_date_time < NOW() - INTERVAL '15' MINUTE) "
        + "ORDER BY submitted_date_time ASC LIMIT 1", nativeQuery = true)
    PendingRequestEntity findOldestPendingRequestForProcessing();

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PROCESSING' WHERE pr.id = :id")
    void markRequestAsProcessing(Long id);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PENDING', pr.retryCount = :retryCount + 1, "
        + "pr.last_tried_date_time = NOW() WHERE pr.id = :id")
    void markRequestAsPendingAndBumpRetryCount(Long id, int retryCount);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'COMPLETED' WHERE pr.id = :id")
    void markRequestAsCompleted(Long id);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'EXCEPTION' WHERE pr.id = :id")
    void markRequestAsException(Long id);

    @Query("SELECT pr FROM PendingRequestEntity pr WHERE pr.submittedDateTime < NOW() "
        + "- INTERVAL '1' DAY AND pr.incidentFlag = false")
    List<PendingRequestEntity> findRequestsForEscalation();

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.incidentFlag = true WHERE pr.submittedDateTime < NOW() "
        + "- INTERVAL '1' DAY AND pr.incidentFlag = false")
    void identifyRequestsForEscalation();

    // @Modifying
    // @Query("DELETE FROM PendingRequestEntity pr WHERE pr.status = 'COMPLETED' "
    //     + "AND pr.submittedDateTime < NOW() - INTERVAL '30' DAY")
    // void deleteCompletedRecords();

    // @Modifying
    // @Query("DELETE FROM PendingRequestEntity pr WHERE pr.status = 'COMPLETED' AND pr.submittedDateTime < :thresholdDateTime")
    // void deleteCompletedRecords(Timestamp thresholdDateTime);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = :status, pr.retryCount = :retryCount WHERE pr.id = :id")
    void updateStatusAndRetryCount(Long id, String status, int retryCount);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PENDING', pr.retryCount = :retryCount WHERE pr.id = :id")
    void markRequestAsPending(Long id, int retryCount);

}

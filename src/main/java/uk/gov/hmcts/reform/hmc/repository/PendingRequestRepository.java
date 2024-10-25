package uk.gov.hmcts.reform.hmc.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.List;

@Transactional(propagation = Propagation.REQUIRES_NEW)
@Repository("pendingRequestRepository")
public interface PendingRequestRepository extends CrudRepository<PendingRequestEntity, Long> {

    @Query(value = "SELECT * FROM public.pending_requests ORDER BY submitted_date_time DESC LIMIT 1",
        nativeQuery = true)
    PendingRequestEntity findLatestRecord();

    @Query(value = "SELECT * FROM public.pending_requests pr1 "
        + "WHERE status = 'PENDING' "
        + "AND (last_tried_date_time IS NULL "
        + "OR last_tried_date_time < NOW() - CAST(:pendingWaitValue || ' ' || :pendingWaitInterval AS INTERVAL)) "
        + "AND (pr1.message_type = 'REQUEST_HEARING' "
        + "   OR (pr1.message_type != 'REQUEST_HEARING' "
        + "       AND NOT EXISTS ( "
        + "           SELECT 1 "
        + "           FROM public.pending_requests pr2 "
        + "           WHERE pr2.status = 'EXCEPTION' "
        + "             AND pr2.hearing_id = pr1.hearing_id "
        + "             AND pr2.submitted_date_time < pr1.submitted_date_time "
        + "       )) "
        + "   ) "
        + "ORDER BY pr1.submitted_date_time ASC "
        + "LIMIT 1;", nativeQuery = true)
    PendingRequestEntity findOldestPendingRequestForProcessing(
        @Param("pendingWaitValue") Long pendingWaitValue,
        @Param("pendingWaitInterval") String pendingWaitInterval);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = :status WHERE pr.id = :id")
    void markRequestWithGivenStatus(Long id, String status);

    @Query(value = "SELECT * FROM public.pending_requests WHERE submitted_date_time < NOW() - "
        + "CAST(:escalationWaitValue || ' ' || :escalationWaitInterval AS INTERVAL) "
        + "AND incident_flag = false",
        nativeQuery = true)
    List<PendingRequestEntity> findRequestsForEscalation(
        @Param("escalationWaitValue") Long escalationWaitValue,
        @Param("escalationWaitInterval") String escalationWaitInterval);

    @Modifying
    @Query(value = "UPDATE public.pending_requests SET incident_flag = true WHERE submitted_date_time < NOW()"
        + " - CAST(:escalationWaitValue || ' ' || :escalationWaitInterval AS INTERVAL) "
        + "AND incident_flag = false",
        nativeQuery = true)
    int identifyRequestsForEscalation(
        @Param("escalationWaitValue") Long escalationWaitValue,
        @Param("escalationWaitInterval") String escalationWaitInterval);

    @Modifying
    @Query(value = "DELETE FROM public.pending_requests WHERE status = 'COMPLETED' AND submitted_date_time < NOW()"
        + " - CAST(:deletionWaitValue || ' ' || :deletionWaitInterval AS INTERVAL)", nativeQuery = true)
    int deleteCompletedRecords(
        @Param("deletionWaitValue") Long deletionWaitValue,
        @Param("deletionWaitInterval") String deletionWaitInterval);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = :status, pr.retryCount = :retryCount WHERE pr.id = :id")
    void updateStatusAndRetryCount(Long id, String status, int retryCount);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PENDING', pr.retryCount = :retryCount WHERE pr.id = :id")
    void markRequestAsPending(Long id, int retryCount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PendingRequestEntity p WHERE p.hearingId = :hearingId")
    List<PendingRequestEntity> findAndLockByHearingId(@Param("hearingId") Long hearingId);

}

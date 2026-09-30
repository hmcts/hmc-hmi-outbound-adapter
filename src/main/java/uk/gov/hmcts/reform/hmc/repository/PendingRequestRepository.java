package uk.gov.hmcts.reform.hmc.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Transactional
@Repository("pendingRequestRepository")
public interface PendingRequestRepository extends CrudRepository<PendingRequestEntity, Long> {

    @Query(value = "SELECT * FROM public.pending_requests ORDER BY submitted_date_time DESC LIMIT 1",
        nativeQuery = true)
    PendingRequestEntity findLatestRecord();

    @Query(value = """
    WITH candidate AS (
        SELECT pr.id
        FROM public.pending_requests pr
        WHERE pr.status = 'PENDING'
          AND (
              pr.last_tried_date_time IS NULL
              OR pr.last_tried_date_time < NOW()
                    - CAST(:retryLimitInMinutes || ' minutes' AS INTERVAL)
          )
          AND NOT EXISTS (
              SELECT 1
              FROM public.pending_requests previous
              WHERE previous.hearing_id = pr.hearing_id
                AND previous.status != 'COMPLETED'
                AND (
                    previous.submitted_date_time < pr.submitted_date_time
                    OR (
                        previous.submitted_date_time = pr.submitted_date_time
                        AND previous.id < pr.id
                    )
                )
          )
        ORDER BY pr.submitted_date_time, pr.id
        FOR UPDATE SKIP LOCKED
        LIMIT 1
    )
    UPDATE public.pending_requests pr
    SET status = 'PROCESSING',
        claimed_at = NOW(),
        claim_token = :claimToken,
        last_tried_date_time = NOW()
    FROM candidate
    WHERE pr.id = candidate.id
    RETURNING pr.*
        """, nativeQuery = true)
    PendingRequestEntity claimNextPendingRequest(
        @Param("retryLimitInMinutes") Long retryLimitInMinutes,
        @Param("claimToken") UUID claimToken
    );

    @Query(value = "SELECT * FROM public.pending_requests WHERE submitted_date_time < NOW() - "
        + "CAST(:escalationWaitValue || ' ' || :escalationWaitInterval AS INTERVAL) "
        + "AND incident_flag = false AND status != 'COMPLETED' ",
        nativeQuery = true)
    List<PendingRequestEntity> findRequestsForEscalation(
        @Param("escalationWaitValue") Long escalationWaitValue,
        @Param("escalationWaitInterval") String escalationWaitInterval);

    @Modifying
    @Query(value = "UPDATE public.pending_requests "
        + "SET incident_flag = true, last_tried_date_time = :lastTriedDateTime "
        + "WHERE id = :id AND incident_flag = false",
        nativeQuery = true)
    int markRequestForEscalation(Long id, LocalDateTime lastTriedDateTime);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'EXCEPTION', pr.claimedAt = null, pr.claimToken = null "
        + "WHERE pr.id = :id AND pr.status = 'PROCESSING' AND pr.claimToken = :claimToken")
    void markClaimedRequestAsException(Long id, UUID claimToken);

    @Modifying
    @Query(value = "UPDATE public.pending_requests SET incident_flag = true, status = 'EXCEPTION', "
        + "claimed_at = NULL, claim_token = NULL WHERE id = :id AND status = 'PROCESSING' "
        + "AND claim_token = :claimToken",
        nativeQuery = true)
    void markClaimedRequestAsExceptionWithIncident(Long id, UUID claimToken);

    @Modifying
    @Query(value = "UPDATE public.pending_requests SET status = 'EXCEPTION', incident_flag = true, "
        + "claimed_at = NULL, claim_token = NULL "
        + "WHERE status = 'PENDING' AND submitted_date_time < :exceptionLimitTime",
        nativeQuery = true)
    int markOverduePendingRequestsAsException(LocalDateTime exceptionLimitTime);

    @Modifying
    @Query(value = "UPDATE public.pending_requests SET status = 'PENDING', "
        + "claimed_at = NULL, claim_token = NULL "
        + "WHERE status = 'PROCESSING' AND claimed_at < :claimTimeLimit",
        nativeQuery = true)
    int resetTimedOutClaimedRequests(LocalDateTime claimTimeLimit);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'COMPLETED', pr.claimedAt = null, pr.claimToken = null "
        + "WHERE pr.id = :id AND pr.status = 'PROCESSING' AND pr.claimToken = :claimToken")
    void completeClaimedRequest(Long id, UUID claimToken);

    @Modifying
    @Query(value = "DELETE FROM public.pending_requests WHERE status = 'COMPLETED' AND submitted_date_time < NOW()"
        + " - CAST(:deletionWaitValue || ' ' || :deletionWaitInterval AS INTERVAL)", nativeQuery = true)
    int deleteCompletedRequests(
        @Param("deletionWaitValue") Long deletionWaitValue,
        @Param("deletionWaitInterval") String deletionWaitInterval);

    @Modifying
    @Query("UPDATE PendingRequestEntity pr SET pr.status = 'PENDING', "
        + "pr.retryCount = pr.retryCount + 1, pr.lastTriedDateTime = CURRENT_TIMESTAMP, "
        + "pr.claimedAt = null, pr.claimToken = null "
        + "WHERE pr.id = :id AND pr.status = 'PROCESSING' AND pr.claimToken = :claimToken")
    void resetFailedClaimedRequest(Long id, UUID claimToken);

}

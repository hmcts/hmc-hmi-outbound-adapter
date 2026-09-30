package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.hmc.config.MessageType.AMEND_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.DELETE_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.REQUEST_HEARING;
import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.COMPLETED;
import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.EXCEPTION;
import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.PENDING;
import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.PROCESSING;

class PendingRequestRepositoryIT extends BaseTest {

    private static final String DELETE_PENDING_REQUEST_DATA_SCRIPT
        = "classpath:sql/delete-pending_request_tables.sql";
    private static final String INSERT_PENDING_REQUESTS_NEW_WITHOUT_EXCEPTION
        = "classpath:sql/insert-pending_requests_new_without_exception.sql";
    private static final String INSERT_PENDING_REQUESTS_NEW_WITH_EXCEPTION
        = "classpath:sql/insert-pending_requests_new_with_exception.sql";
    private static final String INSERT_PENDING_REQUESTS_AMEND_WITHOUT_EXCEPTION
        = "classpath:sql/insert-pending_requests_amend_without_exception.sql";
    private static final String INSERT_PENDING_REQUESTS_AMEND_WITH_EXCEPTION
        = "classpath:sql/insert-pending_requests_amend_with_exception.sql";
    private static final String INSERT_PENDING_REQUESTS_DELETE_WITHOUT_EXCEPTION
        = "classpath:sql/insert-pending_requests_delete_without_exception.sql";
    private static final String INSERT_PENDING_REQUESTS_DELETE_WITH_EXCEPTION
        = "classpath:sql/insert-pending_requests_delete_with_exception.sql";
    private static final String INSERT_PENDING_REQUESTS_NON_RETRIABLE_EXCEPTION
        = "classpath:sql/insert-pending_requests_non_retriable_exception.sql";

    private final PendingRequestRepository pendingRequestRepository;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PendingRequestRepositoryIT(PendingRequestRepository pendingRequestRepository,
                                      JdbcTemplate jdbcTemplate) {
        this.pendingRequestRepository = pendingRequestRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void claimNextPendingRequest_shouldClaimOldestEligibleRequest() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(
            1L, PENDING.name(), REQUEST_HEARING.name(), "oldest",
            LocalDateTime.now().minusHours(1), "101");
        pendingRequest.setLastTriedDateTime(LocalDateTime.now().minusMinutes(30));
        pendingRequestRepository.save(pendingRequest);

        UUID claimToken = UUID.randomUUID();
        PendingRequestEntity claimed = pendingRequestRepository.claimNextPendingRequest(15L, claimToken);

        assertThat(claimed.getId()).isEqualTo(pendingRequest.getId());
        assertThat(claimed.getStatus()).isEqualTo(PROCESSING.name());
        assertThat(claimed.getClaimToken()).isEqualTo(claimToken);
        assertThat(claimed.getClaimedAt()).isNotNull();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void claimNextPendingRequest_shouldKeepRequestsForAHearingInFifoOrder() {
        LocalDateTime submitted = LocalDateTime.now().minusHours(1);
        PendingRequestEntity first = createPendingRequestEntity(
            1L, PENDING.name(), REQUEST_HEARING.name(), "first", submitted, "101");
        first.setLastTriedDateTime(LocalDateTime.now().minusMinutes(30));
        pendingRequestRepository.save(first);

        PendingRequestEntity second = createPendingRequestEntity(
            1L, PENDING.name(), AMEND_HEARING.name(), "second", submitted.plusMinutes(1), "101");
        second.setLastTriedDateTime(LocalDateTime.now().minusMinutes(30));
        pendingRequestRepository.save(second);

        PendingRequestEntity claimedFirst = pendingRequestRepository.claimNextPendingRequest(
            15L, UUID.randomUUID());

        assertThat(claimedFirst.getId()).isEqualTo(first.getId());
        assertThat(pendingRequestRepository.claimNextPendingRequest(15L, UUID.randomUUID()))
            .isNull();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void claimNextPendingRequest_shouldNotClaimRecentlyTriedRequest() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(
            1L, PENDING.name(), REQUEST_HEARING.name(), "recent",
            LocalDateTime.now().minusHours(1), "101");
        pendingRequestRepository.save(pendingRequest);
        jdbcTemplate.update("UPDATE pending_requests "
                                + "SET last_tried_date_time = CURRENT_TIMESTAMP - INTERVAL '1 minute' "
                                + "WHERE id = ?", pendingRequest.getId());

        assertThat(pendingRequestRepository.claimNextPendingRequest(15L, UUID.randomUUID()))
            .isNull();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_NEW_WITHOUT_EXCEPTION})
    void claimNextPendingRequest_whenRequestHearingWithoutException_shouldReturnRequest() {
        PendingRequestEntity claimed = claimNextPendingRequest();

        assertThat(claimed).isNotNull();
        assertThat(claimed.getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(claimed.getHearingId()).isEqualTo(2000000001L);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_NEW_WITH_EXCEPTION})
    void claimNextPendingRequest_whenRequestHearingWithException_shouldReturnNextHearing() {
        PendingRequestEntity claimed = claimNextPendingRequest();

        assertThat(claimed).isNotNull();
        assertThat(claimed.getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(claimed.getHearingId()).isEqualTo(2000000002L);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_AMEND_WITHOUT_EXCEPTION})
    void claimNextPendingRequest_whenAmendHearingWithoutPreviousException_shouldReturnAmendHearing() {
        PendingRequestEntity claimed = claimNextPendingRequest();

        assertThat(claimed).isNotNull();
        assertThat(claimed.getMessageType()).isEqualTo(AMEND_HEARING.name());
        assertThat(claimed.getHearingId()).isEqualTo(2000000001L);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_AMEND_WITH_EXCEPTION})
    void claimNextPendingRequest_whenAmendHearingWithPreviousException_shouldReturnNextHearing() {
        PendingRequestEntity claimed = claimNextPendingRequest();

        assertThat(claimed).isNotNull();
        assertThat(claimed.getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(claimed.getHearingId()).isEqualTo(2000000002L);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_DELETE_WITHOUT_EXCEPTION})
    void claimNextPendingRequest_whenDeleteHearingWithoutPreviousException_shouldReturnDeleteHearing() {
        PendingRequestEntity claimed = claimNextPendingRequest();

        assertThat(claimed).isNotNull();
        assertThat(claimed.getMessageType()).isEqualTo(DELETE_HEARING.name());
        assertThat(claimed.getHearingId()).isEqualTo(2000000001L);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_DELETE_WITH_EXCEPTION})
    void claimNextPendingRequest_whenDeleteHearingWithPreviousException_shouldReturnNextHearing() {
        PendingRequestEntity claimed = claimNextPendingRequest();

        assertThat(claimed).isNotNull();
        assertThat(claimed.getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(claimed.getHearingId()).isEqualTo(2000000002L);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void returnFailedClaimToPending_shouldIncrementRetryCountAndClearClaim() {
        UUID claimToken = UUID.randomUUID();
        PendingRequestEntity pendingRequest = createPendingRequestEntity(
            1L, PROCESSING.name(), REQUEST_HEARING.name(), "failed",
            LocalDateTime.now().minusHours(1), "101");
        pendingRequest.setRetryCount(2);
        pendingRequest.setClaimToken(claimToken);
        pendingRequest.setClaimedAt(LocalDateTime.now());
        pendingRequestRepository.save(pendingRequest);

        pendingRequestRepository.resetFailedClaimedRequest(pendingRequest.getId(), claimToken);

        PendingRequestEntity updated = pendingRequestRepository.findById(pendingRequest.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PENDING.name());
        assertThat(updated.getRetryCount()).isEqualTo(3);
        assertThat(updated.getLastTriedDateTime()).isNotNull();
        assertThat(updated.getClaimToken()).isNull();
        assertThat(updated.getClaimedAt()).isNull();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void recoverTimedOutClaims_shouldReturnOnlyTimedOutClaimedRequests() {
        PendingRequestEntity stale = createPendingRequestEntity(
            1L, PROCESSING.name(), REQUEST_HEARING.name(), "stale",
            LocalDateTime.now().minusHours(1), "101");
        stale.setClaimedAt(LocalDateTime.now().minusMinutes(40));
        stale.setClaimToken(UUID.randomUUID());
        pendingRequestRepository.save(stale);

        PendingRequestEntity active = createPendingRequestEntity(
            2L, PROCESSING.name(), REQUEST_HEARING.name(), "active",
            LocalDateTime.now().minusHours(1), "101");
        active.setClaimedAt(LocalDateTime.now().minusMinutes(5));
        active.setClaimToken(UUID.randomUUID());
        pendingRequestRepository.save(active);

        int recovered = pendingRequestRepository.resetTimedOutClaimedRequests(LocalDateTime.now().minusMinutes(30));

        assertThat(recovered).isEqualTo(1);
        assertThat(pendingRequestRepository.findById(stale.getId()).orElseThrow().getStatus())
            .isEqualTo(PENDING.name());
        assertThat(pendingRequestRepository.findById(active.getId()).orElseThrow().getStatus())
            .isEqualTo(PROCESSING.name());
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void markOverduePendingRequests_shouldMarkOnlyOldPendingRequestsAsException() {
        PendingRequestEntity expired = createPendingRequestEntity(
            1L, PENDING.name(), REQUEST_HEARING.name(), "expired",
            LocalDateTime.now().minusHours(5), "101");
        pendingRequestRepository.save(expired);

        PendingRequestEntity recent = createPendingRequestEntity(
            2L, PENDING.name(), REQUEST_HEARING.name(), "recent",
            LocalDateTime.now().minusHours(1), "101");
        pendingRequestRepository.save(recent);

        int marked = pendingRequestRepository.markOverduePendingRequestsAsException(
            LocalDateTime.now().minusHours(4));

        assertThat(marked).isEqualTo(1);
        assertThat(pendingRequestRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(EXCEPTION.name());
        assertThat(pendingRequestRepository.findById(recent.getId()).orElseThrow().getStatus())
            .isEqualTo(PENDING.name());
    }

    private PendingRequestEntity claimNextPendingRequest() {
        return pendingRequestRepository.claimNextPendingRequest(15L, UUID.randomUUID());
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findRequestsForEscalation_shouldReturnListOfRequests() {
        createTestData(PENDING.name(), LocalDateTime.now().minusDays(3), 1);

        PendingRequestEntity expectedPendingRequest = pendingRequestRepository.findLatestRecord();
        assertThat(expectedPendingRequest.getIncidentFlag()).isFalse();

        createTestData(PENDING.name(), LocalDateTime.now(), 5);

        List<PendingRequestEntity> result = pendingRequestRepository
            .findRequestsForEscalation(1L, "DAY");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(expectedPendingRequest);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findRequestsForEscalation_shouldFindNone() {
        createTestData(PENDING.name(), LocalDateTime.now(), 6);

        List<PendingRequestEntity> result = pendingRequestRepository
            .findRequestsForEscalation(1L, "DAY");
        assertThat(result).isEmpty();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void identifyRequestsForEscalation_shouldUpdateIncidentFlag() {
        createTestData(PENDING.name(), LocalDateTime.now().minusDays(3), 1);

        Iterable<PendingRequestEntity> list = pendingRequestRepository.findAll();
        PendingRequestEntity expectedPendingRequest = list.iterator().next();
        assertThat(expectedPendingRequest.getIncidentFlag()).isFalse();

        createTestData(PENDING.name(), LocalDateTime.now(), 5);

        int identifiedRows = pendingRequestRepository.markRequestForEscalation(1L, LocalDateTime.now());
        assertThat(identifiedRows).isEqualTo(1);

        Optional<PendingRequestEntity> pendingRequestOptional =
            pendingRequestRepository.findById(expectedPendingRequest.getId());
        assertThat(pendingRequestOptional).isPresent();
        PendingRequestEntity pendingRequest = pendingRequestOptional.get();
        assertThat(pendingRequest.getIncidentFlag()).isTrue();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldDeleteRecords() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(COMPLETED.name(),
                                                                         LocalDateTime.now().minusMonths(2));
        pendingRequestRepository.save(pendingRequest);

        int deletedRows = pendingRequestRepository
            .deleteCompletedRequests(30L, "DAYS");
        assertThat(deletedRows).isPositive();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldNotDeleteRecordsNotAged() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(COMPLETED.name(),
                                                                         LocalDateTime.now());
        pendingRequestRepository.save(pendingRequest);

        int deletedRows = pendingRequestRepository
            .deleteCompletedRequests(30L, "DAYS");
        assertThat(deletedRows).isZero();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldHandleNoRecordsToDelete() {
        int deletedRows = pendingRequestRepository
            .deleteCompletedRequests(30L, "DAYS");
        assertThat(deletedRows).isZero();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void shouldMarkRequestForEscalation() {
        createTestData(PENDING.name(), LocalDateTime.now().minusDays(3), 1);
        PendingRequestEntity expectedPendingRequest = pendingRequestRepository.findLatestRecord();
        assertThat(expectedPendingRequest.getIncidentFlag()).isFalse();

        int countMarkedRequests = pendingRequestRepository.markRequestForEscalation(1L, LocalDateTime.now());
        assertThat(countMarkedRequests).isEqualTo(1);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_NON_RETRIABLE_EXCEPTION})
    void shouldMarkClaimedRequestAsExceptionWithIncident() {
        UUID claimToken = UUID.fromString("11111111-1111-1111-1111-111111111111");
        pendingRequestRepository.markClaimedRequestAsExceptionWithIncident(1L, claimToken);

        assertPendingRequestStatusIncidentFlag(1L, EXCEPTION.name(), true);
        assertPendingRequestStatusIncidentFlag(2L, PROCESSING.name(), false);
    }

    private void createTestData(String status, LocalDateTime localDateTime, Integer countOfRecords) {
        for (int i = 0; i < countOfRecords; i++) {
            PendingRequestEntity pendingRequest = createPendingRequestEntity(
                status,
                localDateTime.minusHours(i)
            );
            pendingRequestRepository.save(pendingRequest);
        }
    }

    private PendingRequestEntity createPendingRequestEntity(String status, LocalDateTime localDateTime) {
        return createPendingRequestEntity(1L, status, REQUEST_HEARING.name(), "Test message",
                                          localDateTime, "101");
    }

    private PendingRequestEntity createPendingRequestEntity(Long hearingId, String status, String messageType,
                                                            String message, LocalDateTime localDateTime,
                                                            String deploymentId) {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setHearingId(hearingId);
        pendingRequest.setMessage(message);
        pendingRequest.setMessageType(messageType);
        pendingRequest.setStatus(status);
        pendingRequest.setIncidentFlag(false);
        pendingRequest.setVersionNumber(1);
        pendingRequest.setLastTriedDateTime(localDateTime);
        pendingRequest.setSubmittedDateTime(localDateTime);
        pendingRequest.setRetryCount(0);
        pendingRequest.setDeploymentId(deploymentId);
        return pendingRequest;
    }

    private void assertPendingRequestStatusIncidentFlag(long pendingRequestId, String status, boolean incidentFlag) {
        String messagePrefix = "Pending request id " + pendingRequestId;

        Optional<PendingRequestEntity> pendingRequestOptional = pendingRequestRepository.findById(pendingRequestId);
        assertTrue(pendingRequestOptional.isPresent(), messagePrefix + " should exist");

        PendingRequestEntity pendingRequest = pendingRequestOptional.get();
        assertEquals(status, pendingRequest.getStatus(), messagePrefix + " has unexpected status");
        if (incidentFlag) {
            assertTrue(pendingRequest.getIncidentFlag(), messagePrefix + " incident flag should be true");
        } else {
            assertFalse(pendingRequest.getIncidentFlag(), messagePrefix + " incident flag should be false");
        }
    }
}

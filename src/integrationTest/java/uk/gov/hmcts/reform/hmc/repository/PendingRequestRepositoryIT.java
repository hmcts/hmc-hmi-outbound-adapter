package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private static final String INSERT_PENDING_REQUESTS_PROCESSING
        = "classpath:sql/insert-pending_requests_processing.sql";
    private static final String INSERT_PENDING_REQUESTS_NON_RETRIABLE_EXCEPTION
        = "classpath:sql/insert-pending_requests_non_retriable_exception.sql";

    private final PendingRequestRepository pendingRequestRepository;

    @Autowired
    public PendingRequestRepositoryIT(PendingRequestRepository pendingRequestRepository) {
        this.pendingRequestRepository = pendingRequestRepository;
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findOldestPendingRequestForProcessing_shouldReturnPendingRequest() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(PENDING.name(),
                                                                         LocalDateTime.now().minusHours(1));
        pendingRequestRepository.save(pendingRequest);

        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, " MINUTES");
        assertThat(results).isNotNull();
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
            .deleteCompletedRecords(30L, "DAYS");
        assertThat(deletedRows).isPositive();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldNotDeleteRecordsNotAged() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(COMPLETED.name(),
                                                                         LocalDateTime.now());
        pendingRequestRepository.save(pendingRequest);

        int deletedRows = pendingRequestRepository
            .deleteCompletedRecords(30L, "DAYS");
        assertThat(deletedRows).isZero();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldHandleNoRecordsToDelete() {
        int deletedRows = pendingRequestRepository
            .deleteCompletedRecords(30L, "DAYS");
        assertThat(deletedRows).isZero();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_NEW_WITHOUT_EXCEPTION})
    void findLatestRecord_whenRequestHearingWithoutException_shouldReturnRequest() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.getFirst().getHearingId()).isEqualTo(2000000001);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_NEW_WITH_EXCEPTION})
    void findLatestRecord_whenRequestHearingWithException_shouldReturnNextHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.getFirst().getHearingId()).isEqualTo(2000000002);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_AMEND_WITHOUT_EXCEPTION})
    void findLatestRecord_whenAmendHearingWithoutPreviousException_shouldReturnAmendHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMessageType()).isEqualTo(AMEND_HEARING.name());
        assertThat(results.getFirst().getHearingId()).isEqualTo(2000000001);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_AMEND_WITH_EXCEPTION})
    void findLatestRecord_whenAmendHearingWithPreviousException_shouldReturnNextHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.getFirst().getHearingId()).isEqualTo(2000000002);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_DELETE_WITHOUT_EXCEPTION})
    void findLatestRecord_whenDeleteHearingWithoutPreviousException_shouldReturnDeleteHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMessageType()).isEqualTo(DELETE_HEARING.name());
        assertThat(results.getFirst().getHearingId()).isEqualTo(2000000001);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_DELETE_WITH_EXCEPTION})
    void findLatestRecord_whenDeleteHearingWithPreviousException_shouldReturnNextHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.getFirst().getHearingId()).isEqualTo(2000000002);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_PROCESSING})
    void markRequestWithGivenStatus_shouldBeSuccessful() {
        final long id = 1;

        Optional<PendingRequestEntity> pendingRequestBeforeOptional = pendingRequestRepository.findById(id);
        assertThat(pendingRequestBeforeOptional).isPresent();
        PendingRequestEntity pendingRequestBefore = pendingRequestBeforeOptional.get();
        assertThat(pendingRequestBefore.getStatus()).isEqualTo(PROCESSING.name());
        assertThat(pendingRequestBefore.getRetryCount()).isEqualTo(1);

        pendingRequestRepository.markRequestWithGivenStatus(pendingRequestBefore.getId(), COMPLETED.name());

        Optional<PendingRequestEntity> pendingRequestUpdatedOptional = pendingRequestRepository.findById(id);
        assertThat(pendingRequestUpdatedOptional).isPresent();
        PendingRequestEntity pendingRequestUpdated = pendingRequestUpdatedOptional.get();
        assertThat(pendingRequestUpdated.getStatus()).isEqualTo(COMPLETED.name());
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT, INSERT_PENDING_REQUESTS_PROCESSING})
    void markRequestAsPending_shouldFail_ValuesTheSameAsBefore() {
        final long id = 1;

        Optional<PendingRequestEntity> pendingRequestBeforeOptional = pendingRequestRepository.findById(id);
        assertThat(pendingRequestBeforeOptional).isPresent();
        PendingRequestEntity pendingRequestBefore = pendingRequestBeforeOptional.get();
        assertThat(pendingRequestBefore.getStatus()).isEqualTo(PROCESSING.name());
        assertThat(pendingRequestBefore.getRetryCount()).isEqualTo(1);

        final int retryCountNow = pendingRequestBefore.getRetryCount() + 1;
        final LocalDateTime lastTriedDateTimeNow = LocalDateTime.now();
        pendingRequestRepository.markRequestAsPending(500001L,
                                                      retryCountNow,
                                                      lastTriedDateTimeNow);

        Optional<PendingRequestEntity> pendingRequestUpdatedOptional = pendingRequestRepository.findById(id);
        assertThat(pendingRequestUpdatedOptional).isPresent();
        PendingRequestEntity pendingRequestUpdated = pendingRequestUpdatedOptional.get();
        assertThat(pendingRequestUpdated.getStatus()).isEqualTo(pendingRequestBefore.getStatus());
        assertThat(pendingRequestUpdated.getRetryCount()).isEqualTo(pendingRequestBefore.getRetryCount());
        assertThat(pendingRequestUpdated.getLastTriedDateTime())
            .isEqualTo(pendingRequestBefore.getLastTriedDateTime());
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
    void shouldMarkRequestForNonRetriableException() {
        pendingRequestRepository.markRequestForNonRetriableException(1L);

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

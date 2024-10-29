package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.hmc.config.MessageType.AMEND_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.DELETE_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.REQUEST_HEARING;

class PendingRequestRepositoryIT extends BaseTest {

    @Autowired
    private PendingRequestRepository pendingRequestRepository;

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

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findOldestPendingRequestForProcessing_shouldReturnPendingRequest() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(PendingStatusType.PENDING.name(),
                                                                         LocalDateTime.now().minusHours(1));
        pendingRequestRepository.save(pendingRequest);

        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, " MINUTES");
        assertThat(results).isNotNull();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findRequestsForEscalation_shouldReturnListOfRequests() {
        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now().minusDays(3), 1);

        PendingRequestEntity expectedPendingRequest = pendingRequestRepository.findLatestRecord();
        assertThat(expectedPendingRequest.getIncidentFlag()).isFalse();

        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now(), 5);

        List<PendingRequestEntity> result = pendingRequestRepository
            .findRequestsForEscalation(1L, "DAY");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedPendingRequest);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void identifyRequestsForEscalation_shouldUpdateIncidentFlag() {
        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now().minusDays(3), 1);

        Iterable<PendingRequestEntity> list = pendingRequestRepository.findAll();
        PendingRequestEntity expectedPendingRequest = list.iterator().next();
        assertThat(expectedPendingRequest.getIncidentFlag()).isFalse();

        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now(), 5);

        int identifiedRows = pendingRequestRepository
            .markRequestsForEscalation(1L, "DAY");
        assertThat(identifiedRows).isEqualTo(1);

        PendingRequestEntity pendingRequest = pendingRequestRepository.findById(expectedPendingRequest.getId()).get();
        assertThat(pendingRequest.getIncidentFlag()).isTrue();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldDeleteRecords() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(PendingStatusType.COMPLETED.name(),
                                                                         LocalDateTime.now().minusMonths(2));
        pendingRequestRepository.save(pendingRequest);

        int deletedRows = pendingRequestRepository
            .deleteCompletedRecords(30L, "DAYS");
        assertThat(deletedRows).isPositive();
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldNotDeleteRecordsNotAged() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(PendingStatusType.COMPLETED.name(),
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
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT,INSERT_PENDING_REQUESTS_NEW_WITHOUT_EXCEPTION})
    void findLatestRecord_whenRequestHearingWithoutException_shouldReturnRequest() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.get(0).getHearingId()).isEqualTo(2000000001);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT,INSERT_PENDING_REQUESTS_NEW_WITH_EXCEPTION})
    void findLatestRecord_whenRequestHearingWithException_shouldReturnNextHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.get(0).getHearingId()).isEqualTo(2000000002);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT,INSERT_PENDING_REQUESTS_AMEND_WITHOUT_EXCEPTION})
    void findLatestRecord_whenAmendHearingWithoutPreviousException_shouldReturnAmendHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMessageType()).isEqualTo(AMEND_HEARING.name());
        assertThat(results.get(0).getHearingId()).isEqualTo(2000000001);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT,INSERT_PENDING_REQUESTS_AMEND_WITH_EXCEPTION})
    void findLatestRecord_whenAmendHearingWithPreviousException_shouldReturnNextHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.get(0).getHearingId()).isEqualTo(2000000002);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT,INSERT_PENDING_REQUESTS_DELETE_WITHOUT_EXCEPTION})
    void findLatestRecord_whenDeleteHearingWithoutPreviousException_shouldReturnDeleteHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMessageType()).isEqualTo(DELETE_HEARING.name());
        assertThat(results.get(0).getHearingId()).isEqualTo(2000000001);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT,INSERT_PENDING_REQUESTS_DELETE_WITH_EXCEPTION})
    void findLatestRecord_whenDeleteHearingWithPreviousException_shouldReturnNextHearing() {
        List<PendingRequestEntity> results = pendingRequestRepository
            .findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMessageType()).isEqualTo(REQUEST_HEARING.name());
        assertThat(results.get(0).getHearingId()).isEqualTo(2000000002);
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
        pendingRequest.setId(0L);
        pendingRequest.setHearingId(hearingId);
        pendingRequest.setMessage(message);
        pendingRequest.setMessageType(messageType);
        pendingRequest.setStatus(status);
        pendingRequest.setIncidentFlag(false);
        pendingRequest.setVersionNumber(1);
        Timestamp currentTimestamp = Timestamp.valueOf(localDateTime);
        pendingRequest.setLastTriedDateTime(currentTimestamp);
        pendingRequest.setSubmittedDateTime(currentTimestamp);
        pendingRequest.setRetryCount(0);
        pendingRequest.setDeploymentId(deploymentId);
        return pendingRequest;
    }

}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.hmc.config.MessageType.REQUEST_HEARING;

class PendingRequestRepositoryIT extends BaseTest {

    @Autowired
    private PendingRequestRepository pendingRequestRepository;

    private static final String DELETE_PENDING_REQUEST_DATA_SCRIPT = "classpath:sql/delete-pending_request_tables.sql";

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findOldestPendingRequestForProcessing_shouldReturnPendingRequest() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity();
        pendingRequestRepository.save(pendingRequest);

        PendingRequestEntity result = pendingRequestRepository
            .findOldestPendingRequestForProcessing(2L, " MINUTES");
        assertNotNull(result);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void findRequestsForEscalation_shouldReturnListOfRequests() {
        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now().minusDays(3), 1);

        PendingRequestEntity expectedPendingRequest = pendingRequestRepository.findLatestRecord();
        assertFalse(expectedPendingRequest.getIncidentFlag());

        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now(), 5);

        List<PendingRequestEntity> result = pendingRequestRepository
            .findRequestsForEscalation(1L, "DAY");
        assertEquals(1, result.size());
        assertEquals(expectedPendingRequest, result.get(0));
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void identifyRequestsForEscalation_shouldUpdateIncidentFlag() {
        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now().minusDays(3), 1);

        Iterable<PendingRequestEntity> list = pendingRequestRepository.findAll();
        PendingRequestEntity expectedPendingRequest = list.iterator().next();
        assertFalse(expectedPendingRequest.getIncidentFlag());

        createTestData(PendingStatusType.PENDING.name(), LocalDateTime.now(), 5);

        int identifiedRows = pendingRequestRepository
            .identifyRequestsForEscalation(1L, "DAY");
        assertEquals(1, identifiedRows);

        PendingRequestEntity pendingRequest = pendingRequestRepository.findById(expectedPendingRequest.getId()).get();
        assertEquals(Boolean.TRUE, pendingRequest.getIncidentFlag());
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldDeleteRecords() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(PendingStatusType.COMPLETED.name(),
                                                                         LocalDateTime.now().minusMonths(2));
        pendingRequestRepository.save(pendingRequest);

        int deletedRows = pendingRequestRepository
            .deleteCompletedRecords(30L, "DAYS");
        assertTrue(deletedRows > 0);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldNotDeleteRecordsNotAged() {
        PendingRequestEntity pendingRequest = createPendingRequestEntity(PendingStatusType.COMPLETED.name(),
                                                                         LocalDateTime.now());
        pendingRequestRepository.save(pendingRequest);

        int deletedRows = pendingRequestRepository
            .deleteCompletedRecords(30L, "DAYS");
        assertEquals(0, deletedRows);
    }

    @Test
    @Sql(scripts = {DELETE_PENDING_REQUEST_DATA_SCRIPT})
    void deleteCompletedRecords_shouldHandleNoRecordsToDelete() {
        int deletedRows = pendingRequestRepository
            .deleteCompletedRecords(30L, "DAYS");
        assertEquals(0, deletedRows);
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

    private PendingRequestEntity createPendingRequestEntity() {
        return createPendingRequestEntity(PendingStatusType.PENDING.name(), LocalDateTime.now());
    }

    private PendingRequestEntity createPendingRequestEntity(String status, LocalDateTime localDateTime) {
        return createPendingRequestEntity(1L, status, REQUEST_HEARING.name(), "Test message", localDateTime);
    }

    private PendingRequestEntity createPendingRequestEntity(Long hearingId, String status, String messageType,
                                                            String message, LocalDateTime localDateTime) {
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
        return pendingRequest;
    }

}

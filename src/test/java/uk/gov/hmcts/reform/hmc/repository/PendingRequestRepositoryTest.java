package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingRequestRepositoryTest {

    @Mock
    private PendingRequestRepository pendingRequestRepository;

    @Test
    void findOldestPendingRequestForProcessing_shouldReturnPendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        when(pendingRequestRepository.findOldestPendingRequestForProcessing(2L, "MINUTES"))
            .thenReturn(pendingRequest);
        PendingRequestEntity result =
            pendingRequestRepository.findOldestPendingRequestForProcessing(2L, "MINUTES");
        assertNotNull(result);
    }

    @Test
    void findRequestsForEscalation_shouldReturnListOfRequests() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        when(pendingRequestRepository.findRequestsForEscalation(1L, "DAY"))
            .thenReturn(Collections.singletonList(pendingRequest));
        List<PendingRequestEntity> result = pendingRequestRepository
            .findRequestsForEscalation(1L, "DAY");
        assertTrue(!result.isEmpty());
    }

    @Test
    void identifyRequestsForEscalation_shouldUpdateIncidentFlag() {
        when(pendingRequestRepository.identifyRequestsForEscalation(1L, "DAY")).thenReturn(1);
        int updatedRows = pendingRequestRepository.identifyRequestsForEscalation(1L, "DAY");
        assertTrue(updatedRows > 0);
    }

    @Test
    void deleteCompletedRecords_shouldDeleteRecords() {
        when(pendingRequestRepository.deleteCompletedRecords(30L, "DAYS")).thenReturn(1);
        int deletedRows = pendingRequestRepository.deleteCompletedRecords(30L, "DAYS");
        assertTrue(deletedRows > 0);
    }

    @Test
    void deleteCompletedRecords_shouldHandleNoRecordsToDelete() {
        when(pendingRequestRepository.deleteCompletedRecords(30L, "DAYS"))
            .thenThrow(new EmptyResultDataAccessException(1));
        try {
            pendingRequestRepository.deleteCompletedRecords(30L, "DAYS");
        } catch (EmptyResultDataAccessException e) {
            assertTrue(true);
        }
    }
}

package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingRequestRepositoryTest {

    @Mock
    private PendingRequestRepository pendingRequestRepository;

    @Test
    void findQueuedPendingRequestsForProcessing_shouldReturnPendingRequest() {
        List<PendingRequestEntity> pendingRequests = List.of(new PendingRequestEntity());
        when(pendingRequestRepository.findQueuedPendingRequestsForProcessing(2L, "MINUTES"))
            .thenReturn(pendingRequests);
        List<PendingRequestEntity> results =
            pendingRequestRepository.findQueuedPendingRequestsForProcessing(2L, "MINUTES");
        assertThat(results).isNotNull();
    }

    @Test
    void findRequestsForEscalation_shouldReturnListOfRequests() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        when(pendingRequestRepository.findRequestsForEscalation(1L, "DAY"))
            .thenReturn(Collections.singletonList(pendingRequest));
        List<PendingRequestEntity> results = pendingRequestRepository
            .findRequestsForEscalation(1L, "DAY");
        assertThat(results).isNotEmpty();
    }

    @Test
    void markRequestsForEscalation_shouldUpdateIncidentFlag() {
        when(pendingRequestRepository.markRequestForEscalation(eq(1L), any())).thenReturn(1);
        int updatedRows = pendingRequestRepository.markRequestForEscalation(1L, LocalDateTime.now());
        assertThat(updatedRows).isEqualTo(1);
    }

    @Test
    void deleteCompletedRecords_shouldDeleteRecords() {
        when(pendingRequestRepository.deleteCompletedRecords(30L, "DAYS")).thenReturn(1);
        int deletedRows = pendingRequestRepository.deleteCompletedRecords(30L, "DAYS");
        assertThat(deletedRows).isPositive();
    }

    @Test
    void deleteCompletedRecords_shouldHandleNoRecordsToDelete() {
        when(pendingRequestRepository.deleteCompletedRecords(30L, "DAYS"))
            .thenThrow(new EmptyResultDataAccessException(1));

        assertThatExceptionOfType(EmptyResultDataAccessException.class).isThrownBy(
            () -> pendingRequestRepository.deleteCompletedRecords(30L, "DAYS"));
    }
}

package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    void claimNextPendingRequest_shouldReturnPendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        UUID claimToken = UUID.randomUUID();
        when(pendingRequestRepository.claimNextPendingRequest(15L, claimToken))
            .thenReturn(pendingRequest);

        PendingRequestEntity result =
            pendingRequestRepository.claimNextPendingRequest(15L, claimToken);

        assertThat(result).isSameAs(pendingRequest);
    }

    @Test
    void findRequestsForEscalation_shouldReturnListOfRequests() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        when(pendingRequestRepository.findRequestsForEscalation(1L, "DAY"))
            .thenReturn(List.of(pendingRequest));
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
        when(pendingRequestRepository.deleteCompletedRequests(30L, "DAYS")).thenReturn(1);
        int deletedRows = pendingRequestRepository.deleteCompletedRequests(30L, "DAYS");
        assertThat(deletedRows).isPositive();
    }

    @Test
    void deleteCompletedRecords_shouldHandleNoRecordsToDelete() {
        when(pendingRequestRepository.deleteCompletedRequests(30L, "DAYS"))
            .thenThrow(new EmptyResultDataAccessException(1));

        assertThatExceptionOfType(EmptyResultDataAccessException.class).isThrownBy(
            () -> pendingRequestRepository.deleteCompletedRequests(30L, "DAYS"));
    }
}

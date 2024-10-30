package uk.gov.hmcts.reform.hmc.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PendingRequestServiceImpl")
@ExtendWith(MockitoExtension.class)
class PendingRequestServiceImplTest {

    @Mock
    private PendingRequestRepository pendingRequestRepository;

    @InjectMocks
    private PendingRequestServiceImpl pendingRequestService;

    @Test
    void shouldReturnTrueWhenExceptionLimitExceeded() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf(LocalDateTime.now().minusHours(5)));
        pendingRequestService.escalationWaitInterval = "3,HOURS";
        pendingRequestService.exceptionLimitInHours = 4L;

        boolean result = pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenExceptionLimitNotExceeded() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf(LocalDateTime.now().minusHours(3)));
        pendingRequestService.escalationWaitInterval = "3,HOURS";
        pendingRequestService.exceptionLimitInHours = 4L;

        boolean result = pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleNullSubmittedDateTime() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setSubmittedDateTime(null);

        assertThrows(NullPointerException.class,
                     () -> pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest)
        );
    }

    @Test
    void shouldLockPendingRequestsByHearingId() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        List<PendingRequestEntity> pendingRequests = List.of(pendingRequest);
        when(pendingRequestRepository.findAndLockByHearingId(pendingRequest.getHearingId()))
            .thenReturn(pendingRequests);

        List<PendingRequestEntity> result = pendingRequestService.findAndLockByHearingId(pendingRequest.getHearingId());

        assertThat(pendingRequest).isEqualTo(result.get(0));
        verify(pendingRequestRepository, times(1))
            .findAndLockByHearingId(pendingRequest.getHearingId());
    }

    @Test
    void shouldReturnOldestPendingRequestForProcessing() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf(LocalDateTime.now().minusHours(5)));
        pendingRequestService.pendingWaitInterval = "2,MINUTES";
        when(pendingRequestRepository
                 .findQueuedPendingRequestsForProcessing(2L, "MINUTES"))
                 .thenReturn(List.of(pendingRequest));

        List<PendingRequestEntity> results = pendingRequestService.findQueuedPendingRequestsForProcessing();

        assertThat(results.get(0)).isEqualTo(pendingRequest);
        verify(pendingRequestRepository, times(1))
            .findQueuedPendingRequestsForProcessing(anyLong(), anyString());
    }

    @Test
    void shouldReturnFalseWhenLastTriedDateTimePeriodElapsed() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setLastTriedDateTime(Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)));
        pendingRequestService.retryLimitInMinutes = 20L;

        boolean result = pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenLastTriedDateTimePeriodElapsed() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setLastTriedDateTime(Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)));
        pendingRequestService.retryLimitInMinutes = 20L;

        boolean result = pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleNullLastTriedDateTime() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setLastTriedDateTime(null);

        assertThrows(NullPointerException.class,
                     () -> pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)
        );
    }

    @Test
    void shouldMarkRequestAsProcessing() {
        long id = 1L;
        pendingRequestService.markRequestWithGivenStatus(id, PendingStatusType.PROCESSING.name());

        verify(pendingRequestRepository, times(1)).markRequestWithGivenStatus(id,
                                                                              PendingStatusType.PROCESSING.name());
    }

    @Test
    void shouldMarkRequestAsPending() {
        long id = 1L;
        int retryCount = 1;
        pendingRequestService.markRequestAsPending(id, retryCount);

        verify(pendingRequestRepository, times(1)).markRequestAsPending(id,
                                                                        retryCount + 1);
    }

    @Test
    void shouldMarkRequestAsCompleted() {
        long id = 1L;
        pendingRequestService.markRequestWithGivenStatus(id, PendingStatusType.COMPLETED.name());

        verify(pendingRequestRepository, times(1)).markRequestWithGivenStatus(id,
                                                                              PendingStatusType.COMPLETED.name());
    }

    @Test
    void shouldMarkRequestAsException() {
        long id = 1L;
        pendingRequestService.markRequestWithGivenStatus(id, PendingStatusType.EXCEPTION.name());

        verify(pendingRequestRepository, times(1)).markRequestWithGivenStatus(id,
                                                                              PendingStatusType.EXCEPTION.name());
    }

    @Test
    void shouldDeleteCompletedPendingRequests() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";

        pendingRequestService.deleteCompletedPendingRequests();

        verify(pendingRequestRepository, times(1)).deleteCompletedRecords(30L,
                                                                          "DAYS");
    }

    @Test
    void shouldGetIntervalUnits() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";
        assertThat(pendingRequestService.getIntervalUnits(pendingRequestService.deletionWaitInterval)).isEqualTo(
            30L);
    }

    @Test
    void shouldGetIntervalMeasure() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";
        assertThat(pendingRequestService.getIntervalMeasure(
            pendingRequestService.deletionWaitInterval)).isEqualTo("DAYS");
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        return pendingRequest;
    }

}

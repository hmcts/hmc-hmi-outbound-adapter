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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf(LocalDateTime.now().minusHours(5)));
        pendingRequestService.escalationWaitInterval = "3,HOURS";
        pendingRequestService.exceptionLimitInHours = 4L;

        boolean result = pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest);

        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenExceptionLimitNotExceeded() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf(LocalDateTime.now().minusHours(3)));
        pendingRequestService.escalationWaitInterval = "3,HOURS";
        pendingRequestService.exceptionLimitInHours = 4L;

        boolean result = pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest);

        assertFalse(result);
    }

    @Test
    void shouldHandleNullSubmittedDateTime() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setSubmittedDateTime(null);

        assertThrows(NullPointerException.class, () -> {
            pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest);
        });
    }

    @Test
    void shouldLockPendingRequestsByHearingId() {
        final Long hearingId = 1L;
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setHearingId(hearingId);
        List<PendingRequestEntity> pendingRequests = List.of(pendingRequest);
        when(pendingRequestRepository.findAndLockByHearingId(hearingId)).thenReturn(pendingRequests);

        List<PendingRequestEntity> result = pendingRequestService.findAndLockByHearingId(hearingId);

        assertEquals(pendingRequest, result.get(0));
        verify(pendingRequestRepository, times(1)).findAndLockByHearingId(hearingId);
    }

    @Test
    void shouldReturnOldestPendingRequestForProcessing() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf(LocalDateTime.now().minusHours(5)));
        pendingRequestService.pendingWaitInterval = "2,MINUTES";
        when(pendingRequestRepository.findOldestPendingRequestForProcessing(anyLong(), anyString()))
                 .thenReturn(pendingRequest);

        PendingRequestEntity result = pendingRequestService.findOldestPendingRequestForProcessing();

        assertEquals(pendingRequest, result);
        verify(pendingRequestRepository, times(1))
            .findOldestPendingRequestForProcessing(anyLong(), anyString());
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
        assertEquals(30L, pendingRequestService.getIntervalUnits(pendingRequestService.deletionWaitInterval));
    }

    @Test
    void shouldGetIntervalMeasure() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";
        assertEquals("DAYS", pendingRequestService.getIntervalMeasure(
            pendingRequestService.deletionWaitInterval));
    }

}

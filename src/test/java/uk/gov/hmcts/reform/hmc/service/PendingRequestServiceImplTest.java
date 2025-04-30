package uk.gov.hmcts.reform.hmc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.EXCEPTION;

@DisplayName("PendingRequestServiceImpl")
@ExtendWith(MockitoExtension.class)
class PendingRequestServiceImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HearingRepository hearingRepository;

    @Mock
    private PendingRequestRepository pendingRequestRepository;

    @Mock
    private HearingStatusAuditServiceImpl hearingStatusAuditService;

    @InjectMocks
    private PendingRequestServiceImpl pendingRequestService;

    private static final String EXCEPTION_MESSAGE = "Test Exception";

    @Test
    void shouldReturnTrueWhenExceptionLimitExceeded() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setSubmittedDateTime(LocalDateTime.now().minusHours(5));
        pendingRequestService.escalationWaitInterval = "3,HOURS";
        pendingRequestService.exceptionLimitInHours = 4L;

        boolean result = pendingRequestService.submittedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenExceptionLimitNotExceeded() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setSubmittedDateTime(LocalDateTime.now().minusHours(3));
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
        pendingRequest.setSubmittedDateTime(LocalDateTime.now().minusHours(5));
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
        pendingRequest.setLastTriedDateTime(LocalDateTime.now().minusMinutes(10));
        pendingRequestService.retryLimitInMinutes = 20L;

        boolean result = pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenLastTriedDateTimePeriodElapsed() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setLastTriedDateTime(LocalDateTime.now().minusMinutes(30));
        pendingRequestService.retryLimitInMinutes = 20L;

        boolean result = pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest);

        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleNullLastTriedDateTime() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setLastTriedDateTime(null);

        assertThat(pendingRequestService.lastTriedDateTimePeriodElapsed(pendingRequest)).isTrue();
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
        LocalDateTime lastRetriedDateTime = LocalDateTime.now();
        pendingRequestService.markRequestAsPending(id, retryCount, lastRetriedDateTime);

        verify(pendingRequestRepository, times(1)).markRequestAsPending(eq(id),
                                                                        eq(retryCount + 1),
                                                                        any());
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
        pendingRequestService.markRequestWithGivenStatus(id, EXCEPTION.name());

        verify(pendingRequestRepository, times(1)).markRequestWithGivenStatus(id,
                                                                              EXCEPTION.name());
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

    @Test
    void shouldUpdateHearingStatusToExceptionWhenHearingExists() {
        HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
        CaseHearingRequestEntity caseHearingRequest = new CaseHearingRequestEntity();
        caseHearingRequest.setCaseReference("12345");
        caseHearingRequest.setHmctsServiceCode("serviceCode");
        hearingEntity.setCaseHearingRequests(List.of(caseHearingRequest));
        Optional<HearingEntity> optionalHearingEntity = Optional.of(hearingEntity);
        Exception exception = new Exception(EXCEPTION_MESSAGE);
        when(hearingRepository.findById(anyLong())).thenReturn(optionalHearingEntity);

        pendingRequestService.catchExceptionAndUpdateHearing(hearingEntity.getId(), exception);

        verify(hearingRepository, times(1)).save(any());
        assertThat(hearingEntity.getStatus()).isEqualTo(EXCEPTION.name());
        assertThat(hearingEntity.getErrorDescription()).isEqualTo(EXCEPTION_MESSAGE);
    }

    @Test
    void shouldLogErrorWhenHearingDoesNotExist() {
        Long hearingId = 1L;
        Exception exception = new Exception("Test Exception");
        when(hearingRepository.findById(anyLong())).thenReturn(Optional.empty());

        pendingRequestService.catchExceptionAndUpdateHearing(hearingId, exception);

        verify(hearingRepository, times(0)).save(any());
        verify(hearingStatusAuditService,
               times(0))
                    .saveAuditTriageDetailsWithUpdatedDate(any(), any(), any(), any(), any(), any());
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        return pendingRequest;
    }

}

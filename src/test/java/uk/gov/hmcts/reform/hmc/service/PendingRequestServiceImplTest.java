package uk.gov.hmcts.reform.hmc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.hmc.config.PendingStatusType.EXCEPTION;
import static uk.gov.hmcts.reform.hmc.constants.Constants.EXCEPTION_MESSAGE;

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

    private static final String TEST_EXCEPTION_MESSAGE = "Test Exception";

    private final Logger logger = (Logger) LoggerFactory.getLogger(PendingRequestServiceImpl.class);

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
    void shouldClaim() {
        long id = 1L;
        pendingRequestService.claimRequest(id);

        verify(pendingRequestRepository, times(1)).claimRequest(id);
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
    void shouldEscalatePendingRequests() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequest.setStatus(PendingStatusType.PENDING.name());
        pendingRequest.setIncidentFlag(false);
        pendingRequest.setSubmittedDateTime(LocalDateTime.now().minusHours(5));
        pendingRequestService.escalationWaitInterval = "1,DAY";
        Optional<HearingEntity> hearingEntity = TestingUtil.hearingEntity();
        hearingEntity.get().setErrorDescription("TEST ERROR DESCRIPTION");

        when(pendingRequestRepository.findRequestsForEscalation(1L, "DAY"))
            .thenReturn(List.of(pendingRequest));
        when(hearingRepository.findById(anyLong())).thenReturn(hearingEntity);

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();
        pendingRequestService.escalatePendingRequests();

        verifyLogErrors(listAppender);
        verify(pendingRequestRepository, times(1))
            .findRequestsForEscalation(1L, "DAY");
        verify(pendingRequestRepository, times(1))
            .markRequestForEscalation(any(), any());
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
        Exception exception = new BadFutureHearingRequestException(TEST_EXCEPTION_MESSAGE,
                          TestingUtil.generateErrorDetails(TEST_EXCEPTION_MESSAGE, 400));

        when(hearingRepository.findById(anyLong())).thenReturn(optionalHearingEntity);

        pendingRequestService.catchExceptionAndUpdateHearing(hearingEntity.getId(), exception);

        verify(hearingRepository, times(1)).save(any());
        assertThat(hearingEntity.getStatus()).isEqualTo(EXCEPTION.name());
        assertThat(hearingEntity.getErrorDescription()).isEqualTo(TEST_EXCEPTION_MESSAGE);
    }

    @Test
    void shouldUpdateHearingStatusThrowsAuthenticationException() {
        HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
        CaseHearingRequestEntity caseHearingRequest = new CaseHearingRequestEntity();
        caseHearingRequest.setCaseReference("12345");
        caseHearingRequest.setHmctsServiceCode("serviceCode");
        hearingEntity.setCaseHearingRequests(List.of(caseHearingRequest));
        Optional<HearingEntity> optionalHearingEntity = Optional.of(hearingEntity);
        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        Exception exception = new AuthenticationException("Test Auth Exception", TestingUtil.generateAuthErrorDetails(
            "Test Auth Exception", 1234));

        when(hearingRepository.findById(anyLong())).thenReturn(optionalHearingEntity);

        pendingRequestService.catchExceptionAndUpdateHearing(hearingEntity.getId(), exception);

        verifyLogErrors(listAppender);
        verify(hearingRepository, times(1)).save(any());
        assertThat(hearingEntity.getStatus()).isEqualTo(EXCEPTION.name());
        assertThat(hearingEntity.getErrorDescription()).isEqualTo("Test Auth Exception");
    }

    @Test
    void shouldUpdateHearingStatusThrowsResourceNotFoundException() {
        HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
        CaseHearingRequestEntity caseHearingRequest = new CaseHearingRequestEntity();
        caseHearingRequest.setCaseReference("12345");
        caseHearingRequest.setHmctsServiceCode("serviceCode");
        hearingEntity.setCaseHearingRequests(List.of(caseHearingRequest));
        Optional<HearingEntity> optionalHearingEntity = Optional.of(hearingEntity);
        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();
        Exception exception = new ResourceNotFoundException(TEST_EXCEPTION_MESSAGE);

        when(hearingRepository.findById(anyLong())).thenReturn(optionalHearingEntity);

        pendingRequestService.catchExceptionAndUpdateHearing(hearingEntity.getId(), exception);
        verifyLogErrors(listAppender);

        verify(hearingRepository, times(1)).save(any());
        assertThat(exception).isInstanceOf(ResourceNotFoundException.class);
        assertThat(hearingEntity.getStatus()).isEqualTo(EXCEPTION.name());
        assertThat(hearingEntity.getErrorDescription()).isEqualTo(TEST_EXCEPTION_MESSAGE);
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
        pendingRequest.setHearingId(2000000000L);
        return pendingRequest;
    }

    private static void verifyLogErrors(ListAppender<ILoggingEvent> listAppender) {
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0).getLevel());
        assertEquals(String.format(EXCEPTION_MESSAGE), logsList.get(0).getMessage());
    }

    private @NotNull ListAppender<ILoggingEvent> getILoggingEventListAppender() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

}

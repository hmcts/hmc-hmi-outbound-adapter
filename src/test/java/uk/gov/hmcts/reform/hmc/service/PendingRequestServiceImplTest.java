package uk.gov.hmcts.reform.hmc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.config.MessageSenderToTopicConfiguration;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.helper.hmi.HmiHearingResponseMapper;
import uk.gov.hmcts.reform.hmc.model.HmcHearingResponse;
import uk.gov.hmcts.reform.hmc.model.HmcHearingUpdate;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
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

    @Mock
    private HmiHearingResponseMapper hmiHearingResponseMapper;

    @Mock
    private MessageSenderToTopicConfiguration messageSenderToTopicConfiguration;

    @Mock
    private ServiceBusReceivedMessageContext messageContext = mock(ServiceBusReceivedMessageContext.class);

    @Mock
    private ServiceBusReceivedMessage message;

    private static final String TEST_EXCEPTION_MESSAGE = "Test Exception";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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
    void shouldUpdateHearingStatusThrowsBadRequestException() {
        HearingEntity hearingEntity = TestingUtil.generateHearingEntityWithHearingResponse(2000000000L,
                                                                         HttpStatus.BAD_REQUEST.value(),
                                                                         "version is invalid");
        Exception exception = new BadFutureHearingRequestException(TEST_EXCEPTION_MESSAGE,
                                            TestingUtil.generateErrorDetails(TEST_EXCEPTION_MESSAGE,
                                                                             HttpStatus.BAD_REQUEST.value()));
        testUpdateHearingStatusThrowsException(hearingEntity, exception, TEST_EXCEPTION_MESSAGE);
    }

    @Test
    void shouldUpdateHearingStatusThrowsAuthenticationException() {
        HearingEntity hearingEntity = TestingUtil.generateHearingEntityWithHearingResponse(2000000000L,
                                             HttpStatus.INTERNAL_SERVER_ERROR.value(), "invalid credentials");
        Exception exception = new AuthenticationException("Test Auth Exception", TestingUtil.generateAuthErrorDetails(
            "Test Auth Exception", HttpStatus.INTERNAL_SERVER_ERROR.value()));
        testUpdateHearingStatusThrowsException(hearingEntity, exception, "Test Auth Exception");
    }

    @Test
    void shouldUpdateHearingStatusThrowsResourceNotFoundException() {
        HearingEntity hearingEntity = TestingUtil.generateHearingEntityWithHearingResponse(2000000000L,
                                                 HttpStatus.NOT_FOUND.value(), "invalid credentials");
        Exception exception = new ResourceNotFoundException(TEST_EXCEPTION_MESSAGE);
        testUpdateHearingStatusThrowsException(hearingEntity, exception, TEST_EXCEPTION_MESSAGE);
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

    @Test
    void findByIdShouldReturnPendingRequestWhenIdExists() {
        Long pendingRequestId = 1L;
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(pendingRequestId);
        when(pendingRequestRepository.findById(pendingRequestId)).thenReturn(Optional.of(pendingRequest));

        Optional<PendingRequestEntity> result = pendingRequestService.findById(pendingRequestId);
        assertThat(result)
            .isPresent()
            .contains(pendingRequest);
        verify(pendingRequestRepository, times(1)).findById(pendingRequestId);
    }

    private void testUpdateHearingStatusThrowsException(HearingEntity hearingEntity, Exception exception,
                                                        String expectedErrorDescription) {
        JsonNode data = OBJECT_MAPPER.convertValue(
            generateErrorDetails(expectedErrorDescription, HttpStatus.BAD_REQUEST.value()),
            JsonNode.class);
        when(hearingRepository.findById(hearingEntity.getId())).thenReturn(Optional.of(hearingEntity));
        when(hearingRepository.save(any())).thenReturn(hearingEntity);

        when(hmiHearingResponseMapper.mapEntityToHmcModel(any(), any()))
            .thenReturn(generateHmcResponse(EXCEPTION.name()));
        when(objectMapper.convertValue(any(), eq(JsonNode.class))).thenReturn(data);
        doNothing().when(messageSenderToTopicConfiguration).sendMessage(any(), any(), any(), any());
        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();
        pendingRequestService.catchExceptionAndUpdateHearing(hearingEntity.getId(), exception);
        verify(hearingRepository, times(1)).save(any());
        verify(hearingStatusAuditService, times(1))
            .saveAuditTriageDetailsWithUpdatedDate(any(), any(), any(), any(), any(), any());
        assertThat(hearingEntity.getStatus()).isEqualTo(EXCEPTION.name());
        assertThat(hearingEntity.getErrorDescription()).isEqualTo(expectedErrorDescription);
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
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

    private HmcHearingResponse generateHmcResponse(String status) {
        HmcHearingResponse hmcHearingResponse = new HmcHearingResponse();
        HmcHearingUpdate hmcHearingUpdate = new HmcHearingUpdate();
        hmcHearingUpdate.setHmcStatus(status);
        hmcHearingResponse.setHearingUpdate(hmcHearingUpdate);
        return hmcHearingResponse;
    }

    private ErrorDetails generateErrorDetails(String description, int code) {
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setErrorDescription(description);
        errorDetails.setErrorCode(code);
        return errorDetails;
    }

}

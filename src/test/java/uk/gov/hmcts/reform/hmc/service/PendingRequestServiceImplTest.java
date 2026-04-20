package uk.gov.hmcts.reform.hmc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.config.MessageSenderToTopicConfiguration;
import uk.gov.hmcts.reform.hmc.config.PendingStatusType;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.helper.hmi.HmiHearingResponseMapper;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;
import uk.gov.hmcts.reform.hmc.model.HmcHearingResponse;
import uk.gov.hmcts.reform.hmc.model.HmcHearingUpdate;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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

    @Mock
    private HmiHearingResponseMapper hmiHearingResponseMapper;

    @Mock
    private MessageSenderToTopicConfiguration messageSenderToTopicConfiguration;

    private static final String TEST_EXCEPTION_MESSAGE = "Test Exception";
    private static final String ERROR_MESSAGE =
        "Hearing id: %s with Case reference: %s , Service Code: %s and Error Description: %s updated to status %s";

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

        assertThat(pendingRequest).isEqualTo(result.getFirst());
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

        assertThat(results.getFirst()).isEqualTo(pendingRequest);
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

    @ParameterizedTest
    @MethodSource("hearingsAndExceptions")
    void shouldUpdateHearingStatusForException(HearingEntity hearing,
                                               Exception exception,
                                               String expectedErrorDescription,
                                               int expectedErrorCode) {
        JsonNode data = OBJECT_MAPPER.convertValue(generateErrorDetails(expectedErrorDescription, BAD_REQUEST.value()),
                                                   JsonNode.class);
        when(objectMapper.convertValue(any(), eq(JsonNode.class))).thenReturn(data);
        when(hmiHearingResponseMapper.mapEntityToHmcModel(any(), any())).thenReturn(generateHmcResponse("EXCEPTION"));

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.catchExceptionAndUpdateHearing(hearing, exception);

        logger.detachAndStopAllAppenders();

        String expectedErrorMessage = String.format(ERROR_MESSAGE,
                                                    hearing.getId(),
                                                    "1111222233334444",
                                                    "Test",
                                                    expectedErrorDescription,
                                                    "EXCEPTION");
        verifyLogErrors(listAppender, expectedErrorMessage);

        verify(objectMapper, times(3)).convertValue(any(), eq(JsonNode.class));
        verify(hearingRepository).save(hearing);
        verify(hmiHearingResponseMapper).mapEntityToHmcModel(any(), any());
        verify(messageSenderToTopicConfiguration).sendMessage(any(), any(), any(), any());
        verify(hearingStatusAuditService).saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(any());

        assertThat(hearing.getStatus()).isEqualTo("EXCEPTION");
        assertThat(hearing.getUpdatedDateTime()).isNotNull();
        assertThat(hearing.getErrorDescription()).isEqualTo(expectedErrorDescription);
        assertThat(hearing.getErrorCode()).isEqualTo(expectedErrorCode);
    }

    @Test
    void shouldHandleNonRetriableException() {
        HearingEntity hearing =
            TestingUtil.generateHearingEntityWithHearingResponse(2000000001L, null, null);
        when(hearingRepository.findById(2000000001L)).thenReturn(Optional.of(hearing));

        ErrorDetails errorDetails = TestingUtil.generateErrorDetails("Bad request error", BAD_REQUEST.value());
        JsonNode extractedErrorDetails = OBJECT_MAPPER.convertValue(errorDetails, JsonNode.class);
        when(objectMapper.convertValue(errorDetails, JsonNode.class)).thenReturn(extractedErrorDetails);

        HmcHearingResponse hmcHearingResponse = generateHmcResponse("EXCEPTION");
        hmcHearingResponse.setHmctsServiceCode("Test");
        when(hmiHearingResponseMapper.mapEntityToHmcModel(hearing.getHearingResponses().getFirst(), hearing))
            .thenReturn(hmcHearingResponse);

        JsonNode messageSenderMessage = OBJECT_MAPPER.convertValue(hmcHearingResponse, JsonNode.class);
        when(objectMapper.convertValue(hmcHearingResponse, JsonNode.class)).thenReturn(messageSenderMessage);

        JsonNode hearingStatusAuditErrorDescription = OBJECT_MAPPER.convertValue(extractedErrorDetails, JsonNode.class);
        when(objectMapper.convertValue(extractedErrorDetails, JsonNode.class))
            .thenReturn(hearingStatusAuditErrorDescription);

        PendingRequestEntity pendingRequest = generatePendingRequest();
        BadFutureHearingRequestException exception =
            new BadFutureHearingRequestException(TEST_EXCEPTION_MESSAGE, errorDetails);

        pendingRequestService.handleNonRetriableException(pendingRequest, exception);

        assertThat(hearing.getStatus()).isEqualTo("EXCEPTION");
        assertThat(hearing.getUpdatedDateTime()).isNotNull();
        assertThat(hearing.getErrorDescription()).isEqualTo("Bad request error");
        assertThat(hearing.getErrorCode()).isEqualTo(400);

        verify(hearingRepository).findById(2000000001L);

        verify(objectMapper).convertValue(errorDetails, JsonNode.class);
        verify(hearingRepository).save(hearing);
        verify(objectMapper).convertValue(hmcHearingResponse, JsonNode.class);
        verify(messageSenderToTopicConfiguration)
            .sendMessage(messageSenderMessage.toString(), "Test", "2000000001", null);
        verify(objectMapper).convertValue(extractedErrorDetails, JsonNode.class);
        verify(hearingStatusAuditService).saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(
            HearingStatusAuditContext.builder()
                .hearingEntity(hearing)
                .hearingEvent("list-assist-response")
                .httpStatus("400")
                .source("fh")
                .target("hmc")
                .errorDetails(hearingStatusAuditErrorDescription).build()
        );

        verify(pendingRequestRepository).markRequestForNonRetriableException(1L);
    }

    @Test
    void shouldLogErrorWhenHearingDoesNotExist() {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        Exception exception = new Exception("Test Exception");
        when(hearingRepository.findById(2000000001L)).thenReturn(Optional.empty());

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.handleNonRetriableException(pendingRequest, exception);

        logger.detachAndStopAllAppenders();
        verifyLogErrors(listAppender, "Hearing id 2000000001 not found");

        verify(hearingRepository).findById(2000000001L);
        verify(pendingRequestRepository).markRequestWithGivenStatus(1L, EXCEPTION.name());

        verify(hearingRepository, never()).save(any());
        verify(hearingStatusAuditService, never()).saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(any());
        verify(pendingRequestRepository, never()).markRequestForNonRetriableException(1L);
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

    private static Stream<Arguments> hearingsAndExceptions() {
        return Stream.of(
            arguments(TestingUtil.generateHearingEntityWithHearingResponse(2000000000L,
                                                                           BAD_REQUEST.value(),
                                                                           "version is invalid"),
                      new BadFutureHearingRequestException(TEST_EXCEPTION_MESSAGE,
                                                           TestingUtil.generateErrorDetails(TEST_EXCEPTION_MESSAGE,
                                                                                            BAD_REQUEST.value())),
                      TEST_EXCEPTION_MESSAGE,
                      BAD_REQUEST.value()
            ),
            arguments(TestingUtil.generateHearingEntityWithHearingResponse(2000000000L,
                                                                           INTERNAL_SERVER_ERROR.value(),
                                                                           "invalid credentials"),
                      new AuthenticationException("Test Auth Exception",
                                                  TestingUtil.generateAuthErrorDetails("Test Auth Exception",
                                                                                       INTERNAL_SERVER_ERROR.value())),
                      "Test Auth Exception",
                      INTERNAL_SERVER_ERROR.value()
            ),
            arguments(TestingUtil.generateHearingEntityWithHearingResponse(2000000000L,
                                                                           NOT_FOUND.value(),
                                                                           "invalid credentials"),
                      new ResourceNotFoundException(TEST_EXCEPTION_MESSAGE),
                      TEST_EXCEPTION_MESSAGE,
                      NOT_FOUND.value()
            )
        );
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        return pendingRequest;
    }

    private static void verifyLogErrors(ListAppender<ILoggingEvent> listAppender, String expectedErrorMessage) {
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        ILoggingEvent loggingEvent = logsList.getFirst();
        assertEquals(Level.ERROR, loggingEvent.getLevel());
        assertEquals(expectedErrorMessage, loggingEvent.getFormattedMessage());
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

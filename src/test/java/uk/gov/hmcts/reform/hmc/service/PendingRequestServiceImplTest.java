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
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingResponseEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.ApiClientException;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.errorhandling.ServerErrorException;
import uk.gov.hmcts.reform.hmc.helper.hmi.HmiHearingResponseMapper;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;
import uk.gov.hmcts.reform.hmc.model.HmcHearingResponse;
import uk.gov.hmcts.reform.hmc.model.HmcHearingUpdate;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
    private static final String TEST_AUTH_EXCEPTION_MESSAGE = "Test Auth Exception";
    private static final String TEST_ERROR_DESCRIPTION = "Test Error Description";
    private static final String TEST_AUTH_ERROR_DESCRIPTION = "Test Auth Error";
    private static final String TEST_API_ERROR_DESCRIPTION = "Test Api Error";
    private static final String ERROR_MESSAGE =
        "Hearing id: %s with Case reference: %s , Service Code: %s and Error Description: %s updated to status %s";
    private static final String CASE_REF = "1111222233334444";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Logger logger = (Logger) LoggerFactory.getLogger(PendingRequestServiceImpl.class);

    @Test
    void shouldClaimNextPendingRequest() {
        PendingRequestEntity pendingRequest = generatePendingRequest();
        pendingRequestService.retryLimitInMinutes = 15L;
        when(pendingRequestRepository.claimNextPendingRequest(eq(15L), any(UUID.class))).thenReturn(pendingRequest);

        assertThat(pendingRequestService.claimNextPendingRequest()).isSameAs(pendingRequest);
        verify(pendingRequestRepository).claimNextPendingRequest(eq(15L), any(UUID.class));
    }

    @Test
    void shouldMarkRequestAsPending() {
        UUID claimToken = UUID.randomUUID();

        pendingRequestService.resetFailedClaimedRequest(1L, claimToken);

        verify(pendingRequestRepository).resetFailedClaimedRequest(1L, claimToken);
    }

    @Test
    void shouldCompleteClaimedRequest() {
        UUID claimToken = UUID.randomUUID();

        pendingRequestService.completeClaimedRequest(1L, claimToken);

        verify(pendingRequestRepository)
            .completeClaimedRequest(1L, claimToken);
    }

    @Test
    void shouldDeleteCompletedPendingRequests() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";

        pendingRequestService.deleteCompletedRequests();

        verify(pendingRequestRepository).deleteCompletedRequests(30L, "DAYS");
    }

    @Test
    void deleteCompletedPendingRequestShouldHandleException() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";

        RuntimeException exception = new RuntimeException("Runtime error");
        when(pendingRequestRepository.deleteCompletedRequests(30L, "DAYS")).thenThrow(exception);

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.deleteCompletedRequests();

        logger.detachAndStopAllAppenders();

        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.INFO, "deleteCompletedPendingRequests(30,DAYS)"),
                    new LogMessage(Level.ERROR, "Failed to deleteCompletedRecords"));
        verifyLogMessages(listAppender, expectedLogMessages);

        verify(pendingRequestRepository).deleteCompletedRequests(30L, "DAYS");
    }

    @Test
    void shouldGetIntervalUnits() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";
        assertThat(pendingRequestService.getIntervalUnits(pendingRequestService.deletionWaitInterval)).isEqualTo(30L);
    }

    @Test
    void shouldGetIntervalMeasure() {
        pendingRequestService.deletionWaitInterval = "30,DAYS";
        assertThat(pendingRequestService.getIntervalMeasure(
            pendingRequestService.deletionWaitInterval)).isEqualTo("DAYS");
    }

    @DisplayName("shouldUpdateHearingStatusForException")
    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("nonRetriableExceptions")
    void shouldUpdateHearingStatusForException(Exception exception,
                                               Object errorDetails,
                                               String expectedErrorDescription,
                                               Integer expectedErrorCode) {
        JsonNode errorDetailsJson = OBJECT_MAPPER.convertValue(errorDetails, JsonNode.class);
        when(objectMapper.convertValue(errorDetails, JsonNode.class)).thenReturn(errorDetailsJson);

        HearingResponseEntity hearingResponse = new HearingResponseEntity();
        hearingResponse.setRequestVersion(1);

        HearingEntity hearing = new HearingEntity();
        hearing.setId(2000000000L);
        hearing.setCaseHearingRequests(List.of(TestingUtil.caseHearingRequestEntity()));
        hearing.setHearingResponses(List.of(hearingResponse));

        HmcHearingResponse hmcHearingResponse = generateHmcResponse("EXCEPTION");
        hmcHearingResponse.setHmctsServiceCode("Test");
        when(hmiHearingResponseMapper.mapEntityToHmcModel(hearingResponse, hearing)).thenReturn(hmcHearingResponse);

        JsonNode hmcHearingResponseJson = OBJECT_MAPPER.convertValue(hmcHearingResponse, JsonNode.class);
        when(objectMapper.convertValue(hmcHearingResponse, JsonNode.class)).thenReturn(hmcHearingResponseJson);

        when(objectMapper.convertValue(errorDetailsJson, JsonNode.class)).thenReturn(errorDetailsJson);

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.catchExceptionAndUpdateHearing(hearing, exception);

        logger.detachAndStopAllAppenders();

        String expectedErrorMessage =
            String.format(ERROR_MESSAGE, 2000000000L, CASE_REF, "Test", expectedErrorDescription, "EXCEPTION");
        verifyLogErrors(listAppender, expectedErrorMessage);

        assertThat(hearing.getStatus()).isEqualTo("EXCEPTION");
        assertThat(hearing.getUpdatedDateTime()).isNotNull();
        if (expectedErrorDescription == null) {
            assertThat(hearing.getErrorDescription()).isNull();
        } else {
            assertThat(hearing.getErrorDescription()).isEqualTo(expectedErrorDescription);
        }
        assertThat(hearing.getErrorCode()).isEqualTo(expectedErrorCode);

        verify(objectMapper).convertValue(errorDetails, JsonNode.class);
        verify(hearingRepository).save(hearing);
        verify(hmiHearingResponseMapper).mapEntityToHmcModel(hearingResponse, hearing);
        verify(objectMapper).convertValue(hmcHearingResponse, JsonNode.class);
        verify(messageSenderToTopicConfiguration)
            .sendMessage(hmcHearingResponseJson.toString(), "Test", String.valueOf(2000000000L), null);
        verify(objectMapper).convertValue(errorDetailsJson, JsonNode.class);
        verify(hearingStatusAuditService)
            .saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(any(HearingStatusAuditContext.class));
    }

    @Test
    void shouldHandleUnknownExceptionType() {
        HearingResponseEntity hearingResponse = new HearingResponseEntity();
        hearingResponse.setRequestVersion(1);

        HearingEntity hearing = new HearingEntity();
        hearing.setId(2000000000L);
        hearing.setCaseHearingRequests(List.of(TestingUtil.caseHearingRequestEntity()));
        hearing.setHearingResponses(List.of(hearingResponse));

        HmcHearingResponse hmcHearingResponse = generateHmcResponse("EXCEPTION");
        hmcHearingResponse.setHmctsServiceCode("Test");
        when(hmiHearingResponseMapper.mapEntityToHmcModel(hearingResponse, hearing)).thenReturn(hmcHearingResponse);

        JsonNode hmcHearingResponseJson = OBJECT_MAPPER.convertValue(hmcHearingResponse, JsonNode.class);
        when(objectMapper.convertValue(hmcHearingResponse, JsonNode.class)).thenReturn(hmcHearingResponseJson);

        JsonNode nullNode = OBJECT_MAPPER.convertValue(null, JsonNode.class);
        when(objectMapper.convertValue(null, JsonNode.class)).thenReturn(nullNode);

        RuntimeException exception = new RuntimeException("Runtime error");

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.catchExceptionAndUpdateHearing(hearing, exception);

        logger.detachAndStopAllAppenders();

        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.ERROR,
                                   "Unhandled exception type for hearing id 2000000000, exception class "
                                       + "java.lang.RuntimeException, errorMessage Runtime error"),
                    new LogMessage(Level.ERROR,
                                   String.format(ERROR_MESSAGE, 2000000000L, CASE_REF, "Test", null, "EXCEPTION")));
        verifyLogMessages(listAppender, expectedLogMessages);

        assertThat(hearing.getStatus()).isEqualTo("EXCEPTION");
        assertThat(hearing.getUpdatedDateTime()).isNotNull();
        assertThat(hearing.getErrorDescription()).isNull();
        assertThat(hearing.getErrorCode()).isNull();

        verify(hearingRepository).save(hearing);
        verify(hmiHearingResponseMapper).mapEntityToHmcModel(hearingResponse, hearing);
        verify(objectMapper).convertValue(hmcHearingResponse, JsonNode.class);
        verify(messageSenderToTopicConfiguration)
            .sendMessage(hmcHearingResponseJson.toString(), "Test", String.valueOf(2000000000L), null);
        verify(objectMapper).convertValue(null, JsonNode.class);
        verify(hearingStatusAuditService)
            .saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(any(HearingStatusAuditContext.class));
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
        UUID claimToken = pendingRequest.getClaimToken();

        pendingRequestService.handleNonRetriableException(pendingRequest, exception, claimToken);

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

        verify(pendingRequestRepository).markClaimedRequestAsExceptionWithIncident(1L, claimToken);
    }

    @Test
    void shouldLogErrorWhenHearingDoesNotExist() {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        Exception exception = new Exception("Test Exception");
        when(hearingRepository.findById(2000000001L)).thenReturn(Optional.empty());
        UUID claimToken = pendingRequest.getClaimToken();

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.handleNonRetriableException(pendingRequest, exception, claimToken);

        logger.detachAndStopAllAppenders();
        verifyLogErrors(listAppender, "Hearing id 2000000001 not found");

        verify(hearingRepository).findById(2000000001L);
        verify(pendingRequestRepository).markClaimedRequestAsException(1L, claimToken);

        verify(hearingRepository, never()).save(any());
        verify(hearingStatusAuditService, never()).saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(any());
        verify(pendingRequestRepository, never()).markClaimedRequestAsExceptionWithIncident(1L, claimToken);
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
        verify(pendingRequestRepository).findById(pendingRequestId);
    }

    @Test
    void escalatePendingRequestsShouldHandleException() {
        pendingRequestService.escalationWaitInterval = "1,DAY";

        RuntimeException exception = new RuntimeException("Runtime error");
        when(pendingRequestRepository.findRequestsForEscalation(1L, "DAY")).thenThrow(exception);

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.escalatePendingRequests();

        logger.detachAndStopAllAppenders();

        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.INFO, "escalatePendingRequests()"),
                    new LogMessage(Level.ERROR, "Failed to escalate Pending Requests"));
        verifyLogMessages(listAppender, expectedLogMessages);

        verify(pendingRequestRepository).findRequestsForEscalation(1L, "DAY");
    }

    @Test
    void shouldEscalatePendingRequest() {
        HearingEntity hearing =
            TestingUtil.generateHearingEntityWithHearingResponse(2000000001L, null, null);
        when(hearingRepository.findById(2000000001L)).thenReturn(Optional.of(hearing));

        PendingRequestEntity pendingRequest = generatePendingRequest();

        ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

        pendingRequestService.escalatePendingRequest(pendingRequest);

        logger.detachAndStopAllAppenders();

        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.INFO, "escalatePendingRequests"),
                    new LogMessage(Level.ERROR,
                                   String.format(ERROR_MESSAGE, 2000000001L, CASE_REF, "Test", null, "EXCEPTION")));
        verifyLogMessages(listAppender, expectedLogMessages);

        verify(pendingRequestRepository).markRequestForEscalation(eq(1L), any(LocalDateTime.class));
        verify(hearingRepository).findById(2000000001L);
    }

    private static Stream<Arguments> nonRetriableExceptions() {
        ErrorDetails badFutureHearingRequestErrorDetails =
            TestingUtil.generateErrorDetails(TEST_ERROR_DESCRIPTION, BAD_REQUEST.value());
        BadFutureHearingRequestException badFutureHearingRequestException =
            new BadFutureHearingRequestException(TEST_EXCEPTION_MESSAGE, badFutureHearingRequestErrorDetails);

        ErrorDetails authenticationErrorDetailsNonEmpty =
            TestingUtil.generateAuthErrorDetails(TEST_AUTH_ERROR_DESCRIPTION, INTERNAL_SERVER_ERROR.value());
        AuthenticationException authenticationExceptionNonEmptyErrorDetails =
            new AuthenticationException(TEST_AUTH_EXCEPTION_MESSAGE, authenticationErrorDetailsNonEmpty);

        ErrorDetails authenticationErrorDetailsEmpty = new ErrorDetails();
        AuthenticationException authenticationExceptionEmptyErrorDetails =
            new AuthenticationException(TEST_AUTH_EXCEPTION_MESSAGE, authenticationErrorDetailsEmpty);

        AuthenticationException authenticationExceptionErrorDetailsNull =
            new AuthenticationException(TEST_AUTH_EXCEPTION_MESSAGE);

        ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException(TEST_EXCEPTION_MESSAGE);

        Map<String, Object> apiClientErrorDetails =
            Map.of("errorCode", INTERNAL_SERVER_ERROR.value(),
                   "errorDescription", TEST_ERROR_DESCRIPTION);
        ApiClientException apiClientException =
            new ApiClientException(TEST_EXCEPTION_MESSAGE, INTERNAL_SERVER_ERROR.value(), TEST_ERROR_DESCRIPTION);

        ErrorDetails serverErrorErrorDetailsErrorFields =
            TestingUtil.generateErrorDetails(TEST_ERROR_DESCRIPTION, 9999);
        ServerErrorException serverErrorExceptionErrorFields =
            new ServerErrorException(TEST_EXCEPTION_MESSAGE,
                                     INTERNAL_SERVER_ERROR.value(),
                                     serverErrorErrorDetailsErrorFields);

        ErrorDetails serverErrorErrorDetailsAuthErrorFields =
            TestingUtil.generateAuthErrorDetails(TEST_AUTH_ERROR_DESCRIPTION, 1000);
        ServerErrorException serverErrorExceptionAuthErrorFields =
            new ServerErrorException(TEST_EXCEPTION_MESSAGE,
                                     INTERNAL_SERVER_ERROR.value(),
                                     serverErrorErrorDetailsAuthErrorFields);

        ErrorDetails serverErrorErrorDetailsApiErrorFields = new ErrorDetails();
        serverErrorErrorDetailsApiErrorFields.setApiStatusCode(999);
        serverErrorErrorDetailsApiErrorFields.setApiErrorMessage(TEST_API_ERROR_DESCRIPTION);
        ServerErrorException serverErrorExceptionApiErrorFields =
            new ServerErrorException(TEST_EXCEPTION_MESSAGE,
                                     INTERNAL_SERVER_ERROR.value(),
                                     serverErrorErrorDetailsApiErrorFields);

        ErrorDetails serverErrorErrorDetailsEmpty = new ErrorDetails();
        ServerErrorException serverErrorExceptionEmptyErrorDetails =
            new ServerErrorException(TEST_EXCEPTION_MESSAGE,
                                     INTERNAL_SERVER_ERROR.value(),
                                     serverErrorErrorDetailsEmpty);

        ServerErrorException serverErrorExceptionNullErrorDetails =
            new ServerErrorException(TEST_EXCEPTION_MESSAGE, INTERNAL_SERVER_ERROR.value(), null);

        return Stream.of(
            arguments(named("BadFutureHearingRequestException", badFutureHearingRequestException),
                      badFutureHearingRequestErrorDetails,
                      TEST_ERROR_DESCRIPTION,
                      BAD_REQUEST.value()
            ),
            arguments(named("AuthenticationException - non-empty ErrorDetails",
                            authenticationExceptionNonEmptyErrorDetails),
                      authenticationErrorDetailsNonEmpty,
                      TEST_AUTH_ERROR_DESCRIPTION,
                      INTERNAL_SERVER_ERROR.value()
            ),
            arguments(named("AuthenticationException - empty ErrorDetails", authenticationExceptionEmptyErrorDetails),
                      authenticationErrorDetailsEmpty,
                      null,
                      UNAUTHORIZED.value()
            ),
            arguments(named("AuthenticationException - null ErrorDetails", authenticationExceptionErrorDetailsNull),
                      null,
                      null,
                      UNAUTHORIZED.value()
            ),
            arguments(named("ResourceNotFoundException", resourceNotFoundException),
                      TEST_EXCEPTION_MESSAGE,
                      TEST_EXCEPTION_MESSAGE,
                      NOT_FOUND.value()
            ),
            arguments(named("ApiClientException", apiClientException),
                      apiClientErrorDetails,
                      TEST_ERROR_DESCRIPTION,
                      INTERNAL_SERVER_ERROR.value()
            ),
            arguments(named("ServerErrorException - ErrorDetails error fields", serverErrorExceptionErrorFields),
                      serverErrorErrorDetailsErrorFields,
                      TEST_ERROR_DESCRIPTION,
                      9999
            ),
            arguments(named("ServerErrorException - ErrorDetails auth error fields",
                            serverErrorExceptionAuthErrorFields),
                      serverErrorErrorDetailsAuthErrorFields,
                      TEST_AUTH_ERROR_DESCRIPTION,
                      1000
            ),
            arguments(named("ServerErrorException - ErrorDetails api error fields",
                            serverErrorExceptionApiErrorFields),
                      serverErrorErrorDetailsApiErrorFields,
                      TEST_API_ERROR_DESCRIPTION,
                      999
            ),
            arguments(named("ServerErrorException - empty ErrorDetails", serverErrorExceptionEmptyErrorDetails),
                      serverErrorErrorDetailsEmpty,
                      TEST_EXCEPTION_MESSAGE,
                      INTERNAL_SERVER_ERROR.value()
            ),
            arguments(named("ServerErrorException - null ErrorDetails", serverErrorExceptionNullErrorDetails),
                      null,
                      TEST_EXCEPTION_MESSAGE,
                      INTERNAL_SERVER_ERROR.value()
            )
        );
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(2000000001L);
        pendingRequest.setClaimToken(UUID.randomUUID());
        return pendingRequest;
    }

    private void verifyLogErrors(ListAppender<ILoggingEvent> listAppender, String expectedErrorMessage) {
        verifyLogMessages(listAppender, List.of(new LogMessage(Level.ERROR, expectedErrorMessage)));
    }

    private void verifyLogMessages(ListAppender<ILoggingEvent> listAppender, List<LogMessage> expectedLogMessages) {
        List<ILoggingEvent> logList = listAppender.list;
        assertEquals(expectedLogMessages.size(), logList.size(), "Unexpected number of messages in log");

        String errorMessage = "Log does not contain expected %s message: %s";
        expectedLogMessages
            .forEach(logMessage ->
                         assertTrue(logList.stream()
                                        .anyMatch(logEvent -> logEvent.getLevel() == logMessage.level()
                                            && logEvent.getFormattedMessage().equals(logMessage.message())),
                                    String.format(errorMessage, logMessage.level(), logMessage.message())));
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

    private record LogMessage(Level level, String message) {}
}

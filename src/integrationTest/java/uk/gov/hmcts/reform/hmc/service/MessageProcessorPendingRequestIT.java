package uk.gov.hmcts.reform.hmc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.config.MessageSenderConfiguration;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnToken;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubFailToReturnTokenHtmlResponse;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubRequestHearingThrowingError;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubRequestHearingThrowingErrorHtmlResponse;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyRequestHearing;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubSuccessfullyReturnToken;

class MessageProcessorPendingRequestIT extends BaseTest {

    private static final String TOKEN = "example-token";
    private static final Long PENDING_REQUEST_ID = 1L;
    private static final String HTML_INTERNAL_SERVER_ERROR =
        "<html><head><title>500 Internal Server Error</title></head><body><h1>Internal Server Error</h1></body></html>";

    private static final String DEBUG_LOG_MESSAGE_PROCESS_PENDING_REQUEST_STARTING =
        "processPendingRequest(pendingRequest) starting : %s";

    private static final String DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES =
        "classpath:sql/delete-pending_request_tables.sql";
    private static final String DATA_SCRIPT_DELETE_HEARING_TABLES =
        "classpath:sql/delete-hearing-tables.sql";
    private static final String DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING =
        "classpath:sql/insert-pending_requests_pending_and_hearing.sql";
    private static final String DATA_SCRIPT_INSERT_PENDING_REQUESTS_PROCESSING_AND_HEARING =
        "classpath:sql/insert-pending_requests_processing_and_hearing.sql";
    private static final String DATA_SCRIPT_INSERT_PENDING_REQUESTS_NOT_READY =
        "classpath:sql/insert-pending_requests_not_ready.sql";
    private static final String DATA_SCRIPT_INSERT_PENDING_REQUESTS_UNKNOWN_MESSAGE_TYPE =
        "classpath:sql/insert-pending_requests_unknown_message_type.sql";

    // Mocked so tests will function in an environment without a servicebus
    @MockitoBean
    @SuppressWarnings("unused")
    private MessageSenderConfiguration messageSenderConfiguration;

    private final PendingRequestRepository pendingRequestRepository;

    private final MessageProcessor messageProcessor;

    @Autowired
    public MessageProcessorPendingRequestIT(PendingRequestRepository pendingRequestRepository,
                                            MessageProcessor messageProcessor) {
        this.pendingRequestRepository = pendingRequestRepository;
        this.messageProcessor = messageProcessor;
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING})
    void processPendingRequests_shouldProcessPendingRequests() {
        stubSuccessfullyReturnToken(TOKEN);
        stubSuccessfullyRequestHearing(TOKEN);

        messageProcessor.processPendingRequests();

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatus(pendingRequestAfter, "COMPLETED");
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES})
    void processPendingRequests_noPendingRequests() {
        Logger logger = (Logger) LoggerFactory.getLogger(MessageProcessor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        final Level originalLogLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        messageProcessor.processPendingRequests();

        logger.detachAndStopAllAppenders();
        logger.setLevel(originalLogLevel);

        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.DEBUG, "processPendingRequests (every 120000)- starting"),
                    new LogMessage(Level.DEBUG, "No pending requests found for processing."),
                    new LogMessage(Level.DEBUG, "processPendingRequests - completed"));
        assertLogErrorMessages(listAppender, expectedLogMessages);
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING})
    void processPendingRequest_shouldProcessPendingRequestSuccessfully() {
        stubSuccessfullyReturnToken(TOKEN);
        stubSuccessfullyRequestHearing(TOKEN);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatus(pendingRequestAfter, "COMPLETED");
    }

    @ParameterizedTest
    @MethodSource("notReadyPendingRequests")
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_NOT_READY})
    void processPendingRequest_shouldNotProcessRequestsNotReady(long pendingRequestId, String expectedStatus) {
        Logger logger = (Logger) LoggerFactory.getLogger(MessageProcessor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        final Level originalLogLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(pendingRequestId);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        logger.detachAndStopAllAppenders();
        logger.setLevel(originalLogLevel);

        String notReadyMessage = "Pending request with Id: %s, hearingId: %s is not ready for processing.";
        Long hearingId = pendingRequestBefore.getHearingId();
        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.DEBUG,
                                   String.format(DEBUG_LOG_MESSAGE_PROCESS_PENDING_REQUEST_STARTING, hearingId)),
                    new LogMessage(Level.DEBUG, String.format(notReadyMessage, pendingRequestId, hearingId)));
        assertLogErrorMessages(listAppender, expectedLogMessages);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(pendingRequestId);
        assertPendingRequestStatus(pendingRequestAfter, expectedStatus);
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PROCESSING_AND_HEARING})
    void processPendingRequest_shouldNotProcessClaimedRequest() {
        Logger logger = (Logger) LoggerFactory.getLogger(MessageProcessor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        final Level originalLogLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        logger.detachAndStopAllAppenders();
        logger.setLevel(originalLogLevel);

        List<LogMessage> expectedLogMessages =
            List.of(new LogMessage(Level.DEBUG,
                                   String.format(DEBUG_LOG_MESSAGE_PROCESS_PENDING_REQUEST_STARTING, "2000000000")),
                    new LogMessage(Level.DEBUG, "Pending request with Id: 1, hearingId: 2000000000 already claimed."));
        assertLogErrorMessages(listAppender, expectedLogMessages);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatus(pendingRequestAfter, "PROCESSING");
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_UNKNOWN_MESSAGE_TYPE})
    void processPendingRequest_shouldNotProcessUnknownMessageType() {
        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        final LocalDateTime lastTriedDateTimeBefore = pendingRequestBefore.getLastTriedDateTime();

        messageProcessor.processPendingRequest(pendingRequestBefore);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatus(pendingRequestAfter, "PENDING");
        assertEquals(1, pendingRequestAfter.getRetryCount(), "Pending request retry count should be updated");
        assertTrue(pendingRequestAfter.getLastTriedDateTime().isAfter(lastTriedDateTimeBefore),
                   "Pending request last tried date time should be updated");
    }

    @ParameterizedTest
    @MethodSource("adHttpStatusAndErrors")
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING})
    void processPendingRequest_shouldSetIncidentFlagForAdNonRetriableException(int httpStatus,
                                                                               String errorDescription,
                                                                               List<Integer> errorCodes) {
        stubFailToReturnToken(httpStatus, errorDescription, errorCodes);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatusExceptionAndIncidentFlag(pendingRequestAfter);
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING})
    void processPendingRequest_shouldSetIncidentFlagForAdNonRetriableExceptionNonJson() {
        stubFailToReturnTokenHtmlResponse(500, HTML_INTERNAL_SERVER_ERROR);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatusExceptionAndIncidentFlag(pendingRequestAfter);
    }

    @ParameterizedTest
    @MethodSource("hmiErrorsAndHttpStatus")
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING})
    void processPendingRequest_shouldSetIncidentFlagForHmiNonRetriableException(ErrorDetails errorDetails,
                                                                                int httpStatus) {
        stubSuccessfullyReturnToken(TOKEN);
        stubRequestHearingThrowingError(TOKEN, errorDetails, httpStatus);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatusExceptionAndIncidentFlag(pendingRequestAfter);
    }

    @Test
    @Sql(scripts = {DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PENDING_AND_HEARING})
    void processPendingRequest_shouldSetIncidentFlagForHmiNonRetriableExceptionNonJson() {
        stubSuccessfullyReturnToken(TOKEN);
        stubRequestHearingThrowingErrorHtmlResponse(TOKEN, HTML_INTERNAL_SERVER_ERROR, 500);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(PENDING_REQUEST_ID);
        messageProcessor.processPendingRequest(pendingRequestBefore);

        PendingRequestEntity pendingRequestAfter = getPendingRequest(PENDING_REQUEST_ID);
        assertPendingRequestStatusExceptionAndIncidentFlag(pendingRequestAfter);
    }

    private static Stream<Arguments> adHttpStatusAndErrors() {
        return Stream.of(
            arguments(400, "AADSTS1002012: The provided value for scope scope is not valid.", List.of(1002012)),
            arguments(401, "AADSTS7000215: Invalid client secret provided.", List.of(7000215)),
            arguments(404, "AD Resource not found", List.of(1000000))
        );
    }

    private static Stream<Arguments> hmiErrorsAndHttpStatus() {
        ErrorDetails authenticationErrorDetails = new ErrorDetails();
        authenticationErrorDetails.setAuthErrorCodes(List.of(401));
        authenticationErrorDetails.setAuthErrorDescription("Access denied due to invalid OAuth information");

        ErrorDetails badFutureHearingRequestErrorDetails = new ErrorDetails();
        badFutureHearingRequestErrorDetails.setErrorCode(400);
        badFutureHearingRequestErrorDetails.setErrorDescription("Missing/Invalid Header Source-System");

        ErrorDetails resourceNotFoundErrorDetails = new ErrorDetails();
        resourceNotFoundErrorDetails.setApiStatusCode(404);
        resourceNotFoundErrorDetails.setApiErrorMessage("Resource not found");

        return Stream.of(
            arguments(authenticationErrorDetails, 401),
            arguments(badFutureHearingRequestErrorDetails, 400),
            arguments(resourceNotFoundErrorDetails, 404)
        );
    }

    private static Stream<Arguments> notReadyPendingRequests() {
        return Stream.of(
            arguments(1L, "EXCEPTION"),
            arguments(2L, "PENDING"),
            arguments(3L, "EXCEPTION")
        );
    }

    private PendingRequestEntity getPendingRequest(long pendingRequestId) {
        Optional<PendingRequestEntity> pendingRequestOptional = pendingRequestRepository.findById(pendingRequestId);
        assertTrue(pendingRequestOptional.isPresent(), "Pending request " + pendingRequestId + " should be present");

        return pendingRequestOptional.get();
    }

    private void assertPendingRequestStatus(PendingRequestEntity pendingRequest, String expectedStatus) {
        assertEquals(expectedStatus, pendingRequest.getStatus(),
                     "Pending request " + pendingRequest.getId() + " has unexpected status");
    }

    private void assertPendingRequestStatusExceptionAndIncidentFlag(PendingRequestEntity pendingRequest) {
        assertPendingRequestStatus(pendingRequest, "EXCEPTION");
        assertTrue(pendingRequest.getIncidentFlag(),
                   "Pending request " + pendingRequest.getId() + " incident flag should be true");
    }

    private void assertLogErrorMessages(ListAppender<ILoggingEvent> listAppender,
                                        List<LogMessage> expectedLogMessages) {
        List<ILoggingEvent> logList = listAppender.list;

        assertEquals(expectedLogMessages.size(), logList.size(), "Log contains unexpected number of messages");

        String errorMessage = "Log does not contain expected %s message: %s";
        expectedLogMessages
            .forEach(logMessage ->
                         assertTrue(logList.stream()
                                        .anyMatch(logItem -> logItem.getLevel() == logMessage.level
                                            && logItem.getFormattedMessage().equals(logMessage.message)),
                                    String.format(errorMessage, logMessage.level, logMessage.message)));
    }

    private record LogMessage(Level level, String message) {}
}

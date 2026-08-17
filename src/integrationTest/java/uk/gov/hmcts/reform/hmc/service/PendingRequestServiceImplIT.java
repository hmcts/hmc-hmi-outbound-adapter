package uk.gov.hmcts.reform.hmc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PendingRequestServiceImplIT extends BaseTest {

    private static final String DATA_SCRIPT_DELETE_HEARING_TABLES =
        "classpath:sql/delete-hearing-tables.sql";
    private static final String DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES =
        "classpath:sql/delete-pending_request_tables.sql";
    private static final String DATA_SCRIPT_INSERT_PENDING_REQUESTS_PROCESSING_AND_HEARING =
        "classpath:sql/insert-pending_requests_processing_and_hearing.sql";

    private final PendingRequestRepository pendingRequestRepository;

    private final HearingRepository hearingRepository;

    private final HearingStatusAuditRepository hearingStatusAuditRepository;

    private final PendingRequestServiceImpl pendingRequestService;

    @Autowired
    public PendingRequestServiceImplIT(PendingRequestRepository pendingRequestRepository,
                                       HearingRepository hearingRepository,
                                       HearingStatusAuditRepository hearingStatusAuditRepository,
                                       PendingRequestServiceImpl pendingRequestService) {
        this.pendingRequestRepository = pendingRequestRepository;
        this.hearingRepository = hearingRepository;
        this.hearingStatusAuditRepository = hearingStatusAuditRepository;
        this.pendingRequestService = pendingRequestService;
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("handleNonRetriableExceptionTestData")
    @Sql(scripts = {DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PROCESSING_AND_HEARING})
    void handleNonRetriableException_shouldUpdatePendingRequest(long pendingRequestId,
                                                                boolean expectedIncidentFlag,
                                                                List<String> expectedLogMessages) {
        Logger logger = (Logger) LoggerFactory.getLogger(PendingRequestServiceImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        PendingRequestEntity pendingRequestBefore = getPendingRequest(pendingRequestId);
        ResourceNotFoundException exception = createResourceNotFoundException();

        pendingRequestService.handleNonRetriableException(pendingRequestBefore, exception);

        logger.detachAndStopAllAppenders();

        PendingRequestEntity pendingRequestAfter = getPendingRequest(pendingRequestId);
        assertEquals("EXCEPTION", pendingRequestAfter.getStatus(), "Pending request has unexpected status");
        if (expectedIncidentFlag) {
            assertTrue(pendingRequestAfter.getIncidentFlag(), "Pending request incident flag should be true");
        } else {
            assertFalse(pendingRequestAfter.getIncidentFlag(), "Pending request incident flag should be false");
        }

        assertLogErrorMessages(listAppender, expectedLogMessages);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("catchExceptionAndUpdateHearingTestData")
    @Sql(scripts = {DATA_SCRIPT_DELETE_HEARING_TABLES,
        DATA_SCRIPT_DELETE_PENDING_REQUEST_TABLES,
        DATA_SCRIPT_INSERT_PENDING_REQUESTS_PROCESSING_AND_HEARING})
    void catchExceptionAndUpdateHearing_shouldUpdateHearingAndLogError(Exception exception,
                                                                       Integer expectedErrorCode,
                                                                       String expectedErrorDescription,
                                                                       String expectedAuditErrorDescription,
                                                                       List<String> expectedLogMessages) {
        Logger logger = (Logger) LoggerFactory.getLogger(PendingRequestServiceImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        final LocalDateTime startDateTime = LocalDateTime.now();
        HearingEntity hearingBefore = getHearing();

        pendingRequestService.catchExceptionAndUpdateHearing(hearingBefore, exception);

        logger.detachAndStopAllAppenders();

        HearingEntity hearingAfter = getHearing();
        final LocalDateTime endDateTime = LocalDateTime.now();

        assertEquals("EXCEPTION", hearingAfter.getStatus(), "Hearing has unexpected status");

        LocalDateTime hearingUpdatedDateTime = hearingAfter.getUpdatedDateTime();
        assertTrue(hearingUpdatedDateTime.isAfter(startDateTime) && hearingUpdatedDateTime.isBefore(endDateTime),
                   "Hearing updated date time does not appear to have been changed");

        if (expectedErrorCode == null) {
            assertNull(hearingAfter.getErrorCode(), "Hearing error code should be null");
        } else {
            assertEquals(expectedErrorCode, hearingAfter.getErrorCode(), "Hearing has unexpected error code");
        }
        if (expectedErrorDescription == null) {
            assertNull(hearingAfter.getErrorDescription(), "Hearing error description should be null");
        } else {
            assertEquals(expectedErrorDescription,
                         hearingAfter.getErrorDescription(),
                         "Hearing has unexpected error description");
        }

        List<HearingStatusAuditEntity> hearingStatusAuditEntityList = hearingStatusAuditRepository.findAll();
        assertNotNull(hearingStatusAuditEntityList, "Hearing status audit records should exist");
        assertEquals(1, hearingStatusAuditEntityList.size(), "Unexpected number of hearing status audit records");

        HearingStatusAuditEntity hearingStatusAuditEntity = hearingStatusAuditEntityList.getFirst();
        assertEquals("list-assist-response",
                     hearingStatusAuditEntity.getHearingEvent(),
                     "Hearing status audit has unexpected hearing event");
        assertEquals("400",
                     hearingStatusAuditEntity.getHttpStatus(),
                     "Hearing status audit has unexpected HTTP status");
        assertEquals("fh", hearingStatusAuditEntity.getSource(), "Hearing status audit has unexpected source");
        assertEquals("hmc", hearingStatusAuditEntity.getTarget(), "Hearing status audit has unexpected target");
        if (expectedAuditErrorDescription == null) {
            assertTrue(hearingStatusAuditEntity.getErrorDescription().isEmpty(),
                       "Hearing status audit error description should be empty");
        } else {
            assertEquals(expectedAuditErrorDescription,
                         hearingStatusAuditEntity.getErrorDescription().toString(),
                         "Hearing status audit has unexpected error description");
        }

        assertLogErrorMessages(listAppender, expectedLogMessages);
    }

    private PendingRequestEntity getPendingRequest(long pendingRequestId) {
        Optional<PendingRequestEntity> pendingRequestOptional = pendingRequestRepository.findById(pendingRequestId);
        assertTrue(pendingRequestOptional.isPresent(), "Pending request " + pendingRequestId + " should exist");

        return pendingRequestOptional.get();
    }

    private HearingEntity getHearing() {
        Optional<HearingEntity> hearingOptional = hearingRepository.findById(2000000000L);
        assertTrue(hearingOptional.isPresent(), "Hearing 2000000000 should exist");

        return hearingOptional.get();
    }

    private static Stream<Arguments> handleNonRetriableExceptionTestData() {
        return Stream.of(
            arguments(named("Hearing exists", 1L),
                      true,
                      List.of(createErrorStatusLogMessage("resource not found message"))
            ),
            arguments(named("Hearing does not exist", 2L),
                      false,
                      List.of("Hearing id 2000000001 not found")
            )
        );
    }

    private static Stream<Arguments> catchExceptionAndUpdateHearingTestData() {
        ErrorDetails errorDetailsAuthException = new ErrorDetails();
        errorDetailsAuthException.setAuthErrorCodes(List.of(1000, 2000));
        errorDetailsAuthException.setAuthErrorDescription("auth error description");
        AuthenticationException authenticationException =
            new AuthenticationException("authentication message", errorDetailsAuthException);

        ErrorDetails errorDetailsBadFhrException = new ErrorDetails();
        errorDetailsBadFhrException.setErrorCode(401);
        errorDetailsBadFhrException.setErrorDescription("error description");
        BadFutureHearingRequestException badFutureHearingRequestException =
            new BadFutureHearingRequestException("bad future hearing request message", errorDetailsBadFhrException);

        return Stream.of(
            arguments(named("ResourceNotFoundException", createResourceNotFoundException()),
                      404,
                      "resource not found message",
                      "\"resource not found message\"",
                      List.of(createErrorStatusLogMessage("resource not found message"))
            ),
            arguments(named("AuthenticationException", authenticationException),
                      1000,
                      "auth error description",
                      "{\"error_codes\":[1000,2000],\"error_description\":\"auth error description\"}",
                      List.of(createErrorStatusLogMessage("auth error description"))
            ),
            arguments(named("BadFutureHearingRequestException", badFutureHearingRequestException),
                      401,
                      "error description",
                      "{\"errCode\":401,\"errorDesc\":\"error description\"}",
                      List.of(createErrorStatusLogMessage("error description"))
            ),
            arguments(named("RuntimeException", new RuntimeException("runtime exception")),
                      null,
                      null,
                      null,
                      List.of("Unhandled exception type for hearing id 2000000000, "
                                  + "exception class java.lang.RuntimeException, errorMessage runtime exception",
                              createErrorStatusLogMessage(null))
            )
        );
    }

    private static ResourceNotFoundException createResourceNotFoundException() {
        return new ResourceNotFoundException("resource not found message");
    }

    private static String createErrorStatusLogMessage(String errorDescription) {
        return "Hearing id: 2000000000 with Case reference: 1234123412341238 , "
            + "Service Code: Test and Error Description: " + errorDescription + " updated to status EXCEPTION";
    }

    private void assertLogErrorMessages(ListAppender<ILoggingEvent> listAppender, List<String> expectedLogMessages) {
        List<ILoggingEvent> logList = listAppender.list;

        assertEquals(expectedLogMessages.size(), logList.size(), "Log contains unexpected number of messages");
        expectedLogMessages
            .forEach(logMessage ->
                         assertTrue(logList.stream()
                                        .anyMatch(logItem -> logItem.getLevel() == Level.ERROR
                                            && logItem.getFormattedMessage().equals(logMessage)),
                                    "Log does not contain expected error message: " + logMessage));
    }
}

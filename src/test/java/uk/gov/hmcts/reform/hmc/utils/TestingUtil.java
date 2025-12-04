package uk.gov.hmcts.reform.hmc.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.actuate.health.Status;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingResponseEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.gov.hmcts.reform.hmc.constants.Constants.CREATE_HEARING_REQUEST;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;

public class TestingUtil {

    private TestingUtil() {
    }

    public static HearingStatusAuditEntity hearingStatusAuditEntity() {
        HearingStatusAuditEntity hearingStatusAuditEntity = new HearingStatusAuditEntity();
        hearingStatusAuditEntity.setHmctsServiceId("ABA1");
        hearingStatusAuditEntity.setHearingId("2000000000");
        hearingStatusAuditEntity.setStatus("HEARING_REQUESTED");
        hearingStatusAuditEntity.setHearingEvent(CREATE_HEARING_REQUEST);
        hearingStatusAuditEntity.setHttpStatus(String.valueOf(HttpStatus.SC_OK));
        hearingStatusAuditEntity.setSource(HMC);
        hearingStatusAuditEntity.setTarget(HMI);
        hearingStatusAuditEntity.setRequestVersion("1");
        return hearingStatusAuditEntity;
    }

    public static Optional<HearingStatusAuditEntity> hearingStatusAuditEntity(String hearingEvent, String failureStatus,
                                                                              String source, String target,
                                                                              JsonNode errorDetails) {
        HearingStatusAuditEntity hearingStatusAuditEntity = new HearingStatusAuditEntity();
        hearingStatusAuditEntity.setId(1L);
        hearingStatusAuditEntity.setHmctsServiceId("Test");
        hearingStatusAuditEntity.setHearingId("2000000000");
        hearingStatusAuditEntity.setStatus("HEARING_REQUESTED");
        hearingStatusAuditEntity.setHearingEvent(hearingEvent);
        hearingStatusAuditEntity.setHttpStatus(failureStatus);
        hearingStatusAuditEntity.setSource(source);
        hearingStatusAuditEntity.setTarget(target);
        hearingStatusAuditEntity.setRequestVersion("1");
        hearingStatusAuditEntity.setErrorDescription(errorDetails);
        return Optional.of(hearingStatusAuditEntity);
    }

    public static Optional<HearingEntity> hearingEntity() {
        HearingEntity hearingEntity = new HearingEntity();
        hearingEntity.setId(2000000000L);
        hearingEntity.setStatus("HEARING_REQUESTED");
        hearingEntity.setLinkedOrder(2000000000L);
        CaseHearingRequestEntity caseHearingRequestEntity = caseHearingRequestEntity();
        hearingEntity.setCaseHearingRequests(List.of(caseHearingRequestEntity));
        return Optional.of(hearingEntity);
    }

    public static CaseHearingRequestEntity caseHearingRequestEntity() {
        CaseHearingRequestEntity entity = new CaseHearingRequestEntity();
        entity.setAutoListFlag(false);
        entity.setHearingType("Some hearing type");
        entity.setRequiredDurationInMinutes(10);
        entity.setHearingPriorityType("Priority type");
        entity.setHmctsServiceCode("Test");
        entity.setCaseReference("1111222233334444");
        entity.setCaseUrlContextPath("https://www.google.com");
        entity.setHmctsInternalCaseName("Internal case name");
        entity.setOwningLocationId("CMLC123");
        entity.setCaseRestrictedFlag(true);
        entity.setCaseSlaStartDate(LocalDate.parse("2020-08-10"));
        entity.setVersionNumber(1);
        entity.setHearingRequestReceivedDateTime(LocalDateTime.parse("2020-08-10T12:20:00"));
        return entity;

    }

    public static ErrorDetails generateErrorDetails(String description, Integer code) {
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setErrorDescription(description);
        errorDetails.setErrorCode(code);
        return errorDetails;
    }

    public static ErrorDetails generateAuthErrorDetails(String description, Integer code) {
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setAuthErrorCodes(List.of(code));
        errorDetails.setAuthErrorDescription(description);
        return errorDetails;
    }

    public static HearingEntity generateHearingEntityWithHearingResponse(Long hearingId,
                                                                   Integer errorCode, String errorDescription) {
        HearingEntity entity = new HearingEntity();
        entity.setId(hearingId);
        entity.setErrorCode(errorCode);
        entity.setErrorDescription(errorDescription);
        HearingResponseEntity hearingResponseEntity = new HearingResponseEntity();
        hearingResponseEntity.setRequestVersion(1);
        entity.setHearingResponses(List.of(hearingResponseEntity));
        entity.setCaseHearingRequests(List.of(TestingUtil.caseHearingRequestEntity()));
        return entity;
    }

    public static Stream<Arguments> healthStatuses() {
        return Stream.of(
            arguments(Status.UP),
            arguments(Status.DOWN),
            arguments(Status.UNKNOWN),
            arguments(Status.OUT_OF_SERVICE)
        );
    }

    public static Stream<Arguments> adApiErrorsAndExpectedHealthCheckValues() {
        return Stream.of(
            arguments(named("HTTP 400 response", 400),
                      "AADSTS1002012: The provided value for scope scope is not valid.",
                      List.of(1002012),
                      "ActiveDirectory",
                      "Missing or invalid request parameters",
                      1002012,
                      "AADSTS1002012: The provided value for scope scope is not valid."
            ),
            arguments(named("HTTP 401 response", 401),
                      "AADSTS7000215: Invalid client secret provided.",
                      List.of(7000215),
                      "ActiveDirectory",
                      "Authentication error",
                      7000215,
                      "AADSTS7000215: Invalid client secret provided."
            ),
            arguments(named("HTTP 404 response", 404),
                      "AD Resource not found",
                      List.of(1000000),
                      "ActiveDirectory",
                      "Resource not found",
                      null,
                      null
            ),
            arguments(named("HTTP 500 response", 500),
                      "AD Internal server error",
                      List.of(2000000),
                      "ActiveDirectory",
                      "Server error",
                      2000000,
                      "AD Internal server error"
            )
        );
    }

    public static Stream<Arguments> hmiApiErrorsAndExpectedHealthCheckValues() {
        return Stream.of(
            arguments(named("HTTP 400 response", 400),
                      "Missing/Invalid Header Source-System",
                      "HearingManagementInterface",
                      "Missing or invalid request parameters",
                      400,
                      "Missing/Invalid Header Source-System"
            ),
            arguments(named("HTTP 401 response", 401),
                      "Access denied due to invalid OAuth information",
                      "HearingManagementInterface",
                      "Authentication error",
                      401,
                      "Access denied due to invalid OAuth information"
            ),
            arguments(named("HTTP 404 response", 404),
                      "Resource not found",
                      "HearingManagementInterface",
                      "Resource not found",
                      null,
                      null
            ),
            arguments(named("HTTP 500 response", 500),
                      "Internal server error",
                      "HearingManagementInterface",
                      "Server error",
                      500,
                      "Internal server error"
            )
        );
    }
}

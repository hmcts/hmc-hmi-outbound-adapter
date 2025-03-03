package uk.gov.hmcts.reform.hmc.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static uk.gov.hmcts.reform.hmc.constants.Constants.CREATE_HEARING_REQUEST;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;

public class TestingUtil {

    private TestingUtil() {
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

}

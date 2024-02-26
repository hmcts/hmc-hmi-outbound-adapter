package uk.gov.hmcts.reform.hmc.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAudit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;

public class TestingUtil {

    private TestingUtil() {
    }

    public static HearingStatusAudit hearingStatusAudit() {
        JsonNode jsonNode = null;
        try {
            jsonNode = new ObjectMapper().readTree("{\"query\": {\"match\": \"blah blah\"}}");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        HearingStatusAudit hearingStatusAudit = new HearingStatusAudit();
        hearingStatusAudit.setHearingServiceId("ABA1");
        hearingStatusAudit.setHearingId("2000000000");
        hearingStatusAudit.setStatus("HEARING_REQUESTED");
        hearingStatusAudit.setStatusUpdateDateTime(LocalDateTime.now());
        hearingStatusAudit.setHearingEvent("create-hearing- request");
        hearingStatusAudit.setHttpStatus("200");
        hearingStatusAudit.setSource(HMC);
        hearingStatusAudit.setTarget(HMI);
        hearingStatusAudit.setErrorDescription(jsonNode);
        hearingStatusAudit.setRequestVersion("1");
        return hearingStatusAudit;
    }

    public static HearingStatusAuditEntity hearingStatusAuditEntity() {
        HearingStatusAuditEntity hearingStatusAuditEntity = new HearingStatusAuditEntity();
        hearingStatusAuditEntity.setHmctsServiceId("ABA1");
        hearingStatusAuditEntity.setHearingId("2000000000");
        hearingStatusAuditEntity.setStatus("HEARING_REQUESTED");
        hearingStatusAuditEntity.setHearingEvent("create-hearing- request");
        hearingStatusAuditEntity.setHttpStatus(String.valueOf(HttpStatus.SC_OK));
        hearingStatusAuditEntity.setSource(HMC);
        hearingStatusAuditEntity.setTarget(HMI);
        hearingStatusAuditEntity.setRequestVersion("1");
        return hearingStatusAuditEntity;
    }

    public static Optional<HearingEntity> hearingEntity() {
        HearingEntity hearingEntity = new HearingEntity();
        hearingEntity.setId(1L);
        hearingEntity.setStatus("HEARING_REQUESTED");
        hearingEntity.setLinkedOrder(1L);
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
        entity.setHmctsServiceCode("ABA1");
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
}

package uk.gov.hmcts.reform.hmc.service;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAudit;

import java.time.LocalDateTime;

public interface HearingStatusAuditService {

    void saveStatusAuditTriageDetails(String serviceCode, String hearingId, String status,
                                LocalDateTime statusUpdateDateTime, String hearingEvent, String source,
                                String target, JsonNode errorDescription, String requestVersion);
}

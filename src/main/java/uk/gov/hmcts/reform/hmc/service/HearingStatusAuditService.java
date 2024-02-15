package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.model.HearingStatusAudit;

import java.time.LocalDateTime;

public interface HearingStatusAuditService {

    void saveHearingStatusAudit(HearingStatusAudit hearingStatusAudit);

    HearingStatusAudit mapHearingStatusAuditDetails(String hmctsServiceId,
                                                    String hearingId, String status, LocalDateTime statusUpdatedTime,
                                                    String hearingEvent,
                                                    String source, String target, Object errorDescription,
                                                    String versionNumber);
}

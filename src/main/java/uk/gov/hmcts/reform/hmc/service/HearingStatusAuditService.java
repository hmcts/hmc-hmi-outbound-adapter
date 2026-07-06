package uk.gov.hmcts.reform.hmc.service;

import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;

public interface HearingStatusAuditService {

    void saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(HearingStatusAuditContext hearingStatusAuditContext);

    void saveAuditTriageDetails(HearingStatusAuditContext hearingStatusAuditContext);

}

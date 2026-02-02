package uk.gov.hmcts.reform.hmc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;

import java.time.LocalDateTime;

@Service
@Component
@Slf4j
public class HearingStatusAuditServiceImpl implements HearingStatusAuditService {

    private final HearingStatusAuditRepository hearingStatusAuditRepository;

    @Autowired
    public HearingStatusAuditServiceImpl(HearingStatusAuditRepository hearingStatusAuditRepository) {
        this.hearingStatusAuditRepository = hearingStatusAuditRepository;
    }

    @Override
    public void saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(HearingStatusAuditContext auditContext) {
        HearingStatusAuditEntity hearingStatusAuditEntity = mapHearingStatusAuditDetails(auditContext);
        LocalDateTime ts =
            auditContext.getHearingEntity().getUpdatedDateTime() != null
                ? auditContext.getHearingEntity().getUpdatedDateTime() : LocalDateTime.now();
        hearingStatusAuditEntity.setStatusUpdateDateTime(ts);
        saveHearingStatusAudit(hearingStatusAuditEntity);
    }

    @Override
    public void saveAuditTriageDetails(HearingStatusAuditContext auditContext) {
        HearingStatusAuditEntity hearingStatusAuditEntity = mapHearingStatusAuditDetails(auditContext);
        saveHearingStatusAudit(hearingStatusAuditEntity);
    }

    private HearingStatusAuditEntity mapHearingStatusAuditDetails(HearingStatusAuditContext hearingStatusAuditContext) {
        HearingStatusAuditEntity hearingStatusAuditEntity = new HearingStatusAuditEntity();
        HearingEntity hearingEntity = hearingStatusAuditContext.getHearingEntity();
        hearingStatusAuditEntity.setHmctsServiceId(hearingEntity.getLatestCaseHearingRequest().getHmctsServiceCode());
        hearingStatusAuditEntity.setHearingId(hearingEntity.getId().toString());
        hearingStatusAuditEntity.setStatus(hearingEntity.getStatus());
        hearingStatusAuditEntity.setStatusUpdateDateTime(hearingEntity.getCreatedDateTime());
        hearingStatusAuditEntity.setHearingEvent(hearingStatusAuditContext.getHearingEvent());
        hearingStatusAuditEntity.setHttpStatus(hearingStatusAuditContext.getHttpStatus());
        hearingStatusAuditEntity.setSource(hearingStatusAuditContext.getSource());
        hearingStatusAuditEntity.setTarget(hearingStatusAuditContext.getTarget());
        hearingStatusAuditEntity.setErrorDescription(hearingStatusAuditContext.getErrorDetails());
        hearingStatusAuditEntity.setRequestVersion(hearingEntity.getLatestCaseHearingRequest().getVersionNumber()
                                                       .toString());
        hearingStatusAuditEntity.setResponseDateTime(LocalDateTime.now());
        return hearingStatusAuditEntity;
    }

    private void saveHearingStatusAudit(HearingStatusAuditEntity hearingStatusAuditEntity) {
        hearingStatusAuditRepository.save(hearingStatusAuditEntity);
    }

}

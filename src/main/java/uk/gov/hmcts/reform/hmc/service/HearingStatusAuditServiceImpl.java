package uk.gov.hmcts.reform.hmc.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.helper.HearingStatusAuditMapper;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAudit;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;

@Service
@Component
@Slf4j
public class HearingStatusAuditServiceImpl implements HearingStatusAuditService {

    private final HearingStatusAuditMapper hearingStatusAuditMapper;
    private final HearingStatusAuditRepository hearingStatusAuditRepository;

    @Autowired
    public HearingStatusAuditServiceImpl(HearingStatusAuditRepository hearingStatusAuditRepository,
                                         HearingStatusAuditMapper hearingStatusAuditMapper) {
        this.hearingStatusAuditRepository = hearingStatusAuditRepository;
        this.hearingStatusAuditMapper = hearingStatusAuditMapper;
    }

    @Override
    public void saveAuditTriageDetails(HearingEntity hearingEntity, String hearingEvent,
                                       String httpStatus, String source, String target,
                                       JsonNode errorDescription) {
        HearingStatusAudit hearingStatusAudit = mapHearingStatusAuditDetails(hearingEntity, hearingEvent,
                                                                             httpStatus, source, target,
                                                                             errorDescription);
        saveHearingStatusAudit(hearingStatusAudit);
    }

    private HearingStatusAudit mapHearingStatusAuditDetails(HearingEntity hearingEntity, String hearingEvent,
                                                            String httpStatus, String source, String target,
                                                            JsonNode errorDescription) {
        HearingStatusAudit hearingStatusAudit = new HearingStatusAudit();
        hearingStatusAudit.setHearingServiceId(hearingEntity.getLatestCaseHearingRequest().getHmctsServiceCode());
        hearingStatusAudit.setHearingId(hearingEntity.getId().toString());
        hearingStatusAudit.setStatus(hearingEntity.getStatus());
        hearingStatusAudit.setStatusUpdateDateTime(hearingEntity.getCreatedDateTime());
        hearingStatusAudit.setHearingEvent(hearingEvent);
        hearingStatusAudit.setHttpStatus(httpStatus);
        hearingStatusAudit.setSource(source);
        hearingStatusAudit.setTarget(target);
        hearingStatusAudit.setErrorDescription(errorDescription);
        hearingStatusAudit.setRequestVersion(hearingEntity.getLatestCaseHearingRequest()
                                                 .getVersionNumber().toString());
        return  hearingStatusAudit;
    }

    private void saveHearingStatusAudit(HearingStatusAudit hearingStatusAudit) {
        HearingStatusAuditEntity hearingStatusAuditEntity = hearingStatusAuditMapper
            .modelToEntity(hearingStatusAudit);
        hearingStatusAuditRepository.save(hearingStatusAuditEntity);
    }
}

package uk.gov.hmcts.reform.hmc.service;

import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.helper.HearingStatusAuditMapper;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAudit;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;

import java.util.Map;
import java.util.Optional;

import static uk.gov.hmcts.reform.hmc.constants.Constants.HEARING_ID;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_AUTH;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_FAILURE_STATUS;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;

@Service
@Component
@Slf4j
public class HearingStatusAuditServiceImpl implements HearingStatusAuditService {

    private final HearingStatusAuditMapper hearingStatusAuditMapper;
    private final HearingStatusAuditRepository hearingStatusAuditRepository;
    private final HearingRepository hearingRepository;

    @Autowired
    public HearingStatusAuditServiceImpl(HearingStatusAuditRepository hearingStatusAuditRepository,
                                         HearingStatusAuditMapper hearingStatusAuditMapper,
                                         HearingRepository hearingRepository) {
        this.hearingStatusAuditRepository = hearingStatusAuditRepository;
        this.hearingStatusAuditMapper = hearingStatusAuditMapper;
        this.hearingRepository = hearingRepository;

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

    @SneakyThrows
    @Override
    public void getErrorDetails(ServiceBusReceivedMessageContext messageContext, String errorMessage) {
        Map<String, Object> applicationProperties = messageContext.getMessage().getApplicationProperties();
        String hearingId = applicationProperties.get(HEARING_ID).toString();
        JsonNode errorDetails = new ObjectMapper().readTree("{\"deadLetterReason\": \"" + errorMessage + "\"}");
        Optional<HearingEntity> hearingEntity = hearingRepository.findById(Long.valueOf(hearingId));
        if (hearingEntity.isPresent()) {
            saveAuditTriageDetails(hearingEntity.get(), HMC_TO_HMI_AUTH, HMC_TO_HMI_FAILURE_STATUS,
                                   HMC, HMI, errorDetails);
        }
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

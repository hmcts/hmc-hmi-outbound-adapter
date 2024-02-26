package uk.gov.hmcts.reform.hmc.service;

import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;

import java.util.Optional;

public interface HearingStatusAuditService {

    void saveAuditTriageDetails(Optional<HearingEntity> hearingEntity, String hearingEvent,
                                String httpStatus, String source, String target, JsonNode errorDescription);

    void getErrorDetails(ServiceBusReceivedMessageContext messageContext, String errorMessage);

}

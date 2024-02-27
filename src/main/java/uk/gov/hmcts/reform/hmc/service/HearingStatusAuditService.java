package uk.gov.hmcts.reform.hmc.service;

import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;

public interface HearingStatusAuditService {

    void saveAuditTriageDetails(HearingEntity hearingEntity, String hearingEvent,
                                String httpStatus, String source, String target, JsonNode errorDescription);

    void getErrorDetails(ServiceBusReceivedMessageContext messageContext, String errorMessage);

}

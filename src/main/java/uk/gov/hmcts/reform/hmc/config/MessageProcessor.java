package uk.gov.hmcts.reform.hmc.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.Map;

@Slf4j
@Component
public class MessageProcessor {

    private final ApplicationParams applicationParams;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final HearingManagementInterfaceApiClient hmiClient;

    public MessageProcessor(ApplicationParams applicationParams,
                            ActiveDirectoryApiClient activeDirectoryApiClient,
                            HearingManagementInterfaceApiClient hmiClient) {
        this.applicationParams = applicationParams;
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.hmiClient = hmiClient;
    }

    public void processMessage(JsonNode message, MessageType messageType, Map<String, Object> applicationProperties) {
        if (messageType != null) {

            DefaultFutureHearingRepository defaultFutureHearingRepository =
                new DefaultFutureHearingRepository(
                    activeDirectoryApiClient,
                    applicationParams,
                    hmiClient
                );

            switch (messageType) {
                case REQUEST_HEARING:
                    log.info("Message of type REQUEST_HEARING received");
                    defaultFutureHearingRepository.createHearingRequest(message);
                    break;
                case AMEND_HEARING:
                    log.info("Message of type AMEND_HEARING received");
                    defaultFutureHearingRepository.amendHearingRequest(
                        message,
                        applicationProperties.get("caseListingID").toString()
                    );
                    break;
                case DELETE_HEARING:
                    log.info("Message of type DELETE_HEARING received");
                    defaultFutureHearingRepository.deleteHearingRequest(
                        message,
                        applicationProperties.get("caseListingID").toString()
                    );
                    break;
                default:
                    log.info("Message has unsupported value for message_type");
                    // add to dead letter queue - unsupported message type
                    break;
            }
        } else {
            // add to dead letter queue - unsupported message type
            log.info("Message is missing custom header message_type");
        }

    }
}

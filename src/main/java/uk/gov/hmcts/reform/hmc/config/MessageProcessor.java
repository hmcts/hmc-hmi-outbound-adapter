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
    private final DefaultFutureHearingRepository futureHearingRepository;
    private static final String CASE_LISTING_ID = "hearing_id";

    public MessageProcessor(ApplicationParams applicationParams,
                            ActiveDirectoryApiClient activeDirectoryApiClient,
                            HearingManagementInterfaceApiClient hmiClient,
                            DefaultFutureHearingRepository futureHearingRepository) {
        this.applicationParams = applicationParams;
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.hmiClient = hmiClient;
        this.futureHearingRepository = futureHearingRepository;
    }

    public void processMessage(JsonNode message, MessageType messageType, Map<String, Object> applicationProperties) {
        if (messageType != null) {

            switch (messageType) {
                case REQUEST_HEARING:
                    log.debug("Message of type REQUEST_HEARING received");
                    futureHearingRepository.createHearingRequest(message);
                    break;
                case AMEND_HEARING:
                    log.debug("Message of type AMEND_HEARING received");
                    futureHearingRepository.amendHearingRequest(
                        message,
                        applicationProperties.get(CASE_LISTING_ID).toString()
                    );
                    break;
                case DELETE_HEARING:
                    log.debug("Message of type DELETE_HEARING received");
                    futureHearingRepository.deleteHearingRequest(
                        message,
                        applicationProperties.get(CASE_LISTING_ID).toString()
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

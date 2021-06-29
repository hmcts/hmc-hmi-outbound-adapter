package uk.gov.hmcts.reform.hmc.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.errorhandling.MalformedMessageException;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import java.util.Map;

import static uk.gov.hmcts.reform.hmc.config.MessageType.AMEND_HEARING;
import static uk.gov.hmcts.reform.hmc.config.MessageType.DELETE_HEARING;

@Slf4j
@Component
public class MessageProcessor {

    private final ApplicationParams applicationParams;
    private final ActiveDirectoryApiClient activeDirectoryApiClient;
    private final HearingManagementInterfaceApiClient hmiClient;
    private static final String MESSAGE_TYPE = "message_type";
    public static final String MISSING_CASE_LISTING_ID = "Message is missing custom header caseListingID";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "Message has unsupported value for message_type";
    public static final String MISSING_MESSAGE_TYPE = "Message is missing custom header message_type";

    public MessageProcessor(ApplicationParams applicationParams,
                            ActiveDirectoryApiClient activeDirectoryApiClient,
                            HearingManagementInterfaceApiClient hmiClient
    ) {
        this.activeDirectoryApiClient = activeDirectoryApiClient;
        this.applicationParams = applicationParams;
        this.hmiClient = hmiClient;
    }

    public void processMessage(JsonNode message, Map<String, Object> applicationProperties) {
        if (applicationProperties.containsKey(MESSAGE_TYPE)) {
            MessageType messageType;
            try {
                messageType =
                    MessageType.valueOf(applicationProperties.get(MESSAGE_TYPE).toString());
            } catch (Exception exception) {
                throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }

            DefaultFutureHearingRepository defaultFutureHearingRepository =
                new DefaultFutureHearingRepository(
                    activeDirectoryApiClient,
                    applicationParams,
                    hmiClient
                );
            String caseListingID = null;
            if (messageType.equals(AMEND_HEARING) || messageType.equals(DELETE_HEARING)) {
                try {
                    caseListingID = applicationProperties.get("caseListingID").toString();
                } catch (Exception exception) {
                    throw new MalformedMessageException(MISSING_CASE_LISTING_ID);
                }
            }

            switch (messageType) {
                case REQUEST_HEARING:
                    log.info("Message of type REQUEST_HEARING received");
                    defaultFutureHearingRepository.createHearingRequest(message);
                    break;
                case AMEND_HEARING:
                    log.info("Message of type AMEND_HEARING received");
                    defaultFutureHearingRepository.amendHearingRequest(
                        message, caseListingID
                    );
                    break;
                case DELETE_HEARING:
                    log.info("Message of type DELETE_HEARING received");
                    defaultFutureHearingRepository.deleteHearingRequest(
                        message, caseListingID
                    );
                    break;
                default:
                    throw new MalformedMessageException(UNSUPPORTED_MESSAGE_TYPE);
            }
        } else {
            throw new MalformedMessageException(MISSING_MESSAGE_TYPE);
        }
    }
}

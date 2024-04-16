package uk.gov.hmcts.reform.hmc.errorhandling;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.constants.Constants;

import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_PROCESSING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HEARING_ID;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_HMI_OUTBOUND_ADAPTER;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.NOT_DEFINED;
import static uk.gov.hmcts.reform.hmc.constants.Constants.READ;

@Slf4j
@Component
public class RequestErrorHandler {

    public static final String MESSAGE_PARSE_ERROR = "Unable to parse incoming message with id '{}'";
    public static final String APPLICATION_ERROR = "Unable to process incoming message with id '{}";
    public static final String RETRY_MESSAGE = "Retrying message with id '{}'";
    public static final String RETRIES_EXCEEDED = "Max delivery count reached. Message with id '{}' was dead lettered";

    public void handleJsonError(JsonProcessingException exception, String messageId) {
        log.error(MESSAGE_PARSE_ERROR, messageId, exception);
        // You can add your error handling logic here
    }

    public void handleApplicationError(Exception exception, String messageId) {
        final Long deliveryCount = messageId != null ? Long.parseLong(messageId) : 0L;
        if (deliveryCount >= 3) {
            log.error(APPLICATION_ERROR, messageId, exception);
            // You can add your error handling logic here for retries exceeded
        } else {
            log.warn(RETRY_MESSAGE, messageId);
            log.error(
                ERROR_PROCESSING_MESSAGE,
                HMC_HMI_OUTBOUND_ADAPTER,
                HMC_TO_HMI,
                READ,
                NOT_DEFINED
            );
        }
    }

    public void handleGenericError(Exception exception, String messageId) {
        log.error(APPLICATION_ERROR, messageId, exception);
        // You can add your error handling logic here for generic errors
    }
}

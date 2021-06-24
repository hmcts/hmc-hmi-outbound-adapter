package uk.gov.hmcts.reform.hmc.errorhandling;

import com.azure.messaging.servicebus.models.DeadLetterOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeadLetterService {

    public static final String APPLICATION_PROCESSING_ERROR = "Application processing error";
    public static final String MESSAGE_DESERIALIZATION_ERROR = "Message deserialization error";

    public DeadLetterOptions handleApplicationError(String errorMessage) {
        return setDeadLetterOptions(APPLICATION_PROCESSING_ERROR, errorMessage);
    }

    public DeadLetterOptions handleParsingError(String errorMessage) {
        return setDeadLetterOptions(MESSAGE_DESERIALIZATION_ERROR, errorMessage);
    }

    private DeadLetterOptions setDeadLetterOptions(String reason, String errorDescription) {
        DeadLetterOptions deadLetterOptions = new DeadLetterOptions();
        deadLetterOptions.setDeadLetterReason(reason);
        deadLetterOptions.setDeadLetterErrorDescription(errorDescription);
        return deadLetterOptions;
    }
}


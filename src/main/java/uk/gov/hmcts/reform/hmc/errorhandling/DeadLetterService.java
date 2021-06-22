package uk.gov.hmcts.reform.hmc.errorhandling;

import com.azure.messaging.servicebus.models.DeadLetterOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.client.futurehearing.DeadLetterMessage;

@Slf4j
@Service
public class DeadLetterService {

    public static final String APPLICATION_PROCESSING_ERROR = "Application processing error";
    public static final String MESSAGE_DESERIALIZATION_ERROR = "Message deserialization error";

    public DeadLetterOptions handleApplicationError(String originalMessage, String errorMessage) {
        return createDeadLetterOptions(originalMessage, errorMessage, APPLICATION_PROCESSING_ERROR);
    }

    public DeadLetterOptions handleParsingError(String originalMessage, String errorMessage) {
        return createDeadLetterOptions(originalMessage, errorMessage, MESSAGE_DESERIALIZATION_ERROR);
    }

    private DeadLetterOptions setDeadLetterOptions(String reason, String errorDescription) {
        DeadLetterOptions deadLetterOptions = new DeadLetterOptions();
        deadLetterOptions.setDeadLetterReason(reason);
        deadLetterOptions.setDeadLetterErrorDescription(errorDescription);
        return deadLetterOptions;
    }

    private DeadLetterOptions createDeadLetterOptions(String originalMessage, String errorMessage, String reason) {
        DeadLetterMessage deadLetterObject = new DeadLetterMessage(originalMessage, errorMessage);
        String deadLetterDescription = String.valueOf(deadLetterObject);
        return setDeadLetterOptions(reason, deadLetterDescription);

    }
}


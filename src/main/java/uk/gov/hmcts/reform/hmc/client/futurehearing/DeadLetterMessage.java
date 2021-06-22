package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Data
public final class DeadLetterMessage {

    private final String originalMessage;

    private final String errorDescription;

    @JsonCreator
    public DeadLetterMessage(String originalMessage,
                             String errorDescription) {
        this.originalMessage = originalMessage;
        this.errorDescription = errorDescription;
    }
}

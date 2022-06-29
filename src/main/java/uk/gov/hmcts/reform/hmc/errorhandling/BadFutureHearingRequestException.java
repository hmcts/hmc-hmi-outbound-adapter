package uk.gov.hmcts.reform.hmc.errorhandling;

import lombok.Getter;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;

@Getter
public class BadFutureHearingRequestException extends RuntimeException {

    private final ErrorDetails errorDetails;

    public BadFutureHearingRequestException(String message, ErrorDetails errorDetails) {
        super(message);
        this.errorDetails = errorDetails;
    }
}

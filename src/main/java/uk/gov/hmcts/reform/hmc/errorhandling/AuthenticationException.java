package uk.gov.hmcts.reform.hmc.errorhandling;

import lombok.Getter;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;

@Getter
public class AuthenticationException extends RuntimeException {

    private final ErrorDetails errorDetails;

    public AuthenticationException(String message) {
        super(message);
        this.errorDetails = null;
    }

    public AuthenticationException(String message, ErrorDetails errorDetails) {
        super(message);
        this.errorDetails = errorDetails;
    }

}

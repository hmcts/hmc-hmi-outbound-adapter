package uk.gov.hmcts.reform.hmc.errorhandling;

public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }
}

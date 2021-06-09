package uk.gov.hmcts.reform.hmc.errorhandling;

public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}

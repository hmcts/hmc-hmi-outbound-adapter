package uk.gov.hmcts.reform.hmc.errorhandling;

public class MalformedMessageException extends RuntimeException {

    public MalformedMessageException(String message) {
        super(message);
    }
}

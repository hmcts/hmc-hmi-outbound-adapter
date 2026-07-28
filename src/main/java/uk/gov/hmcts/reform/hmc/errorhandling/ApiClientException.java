package uk.gov.hmcts.reform.hmc.errorhandling;

import lombok.Getter;

import java.io.Serial;

@Getter
public class ApiClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 5409133062761321781L;

    private final Integer errorCode;
    private final String errorDescription;

    public ApiClientException(String message, Integer errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.errorDescription = null;
    }

    public ApiClientException(String message, Integer errorCode, String errorDescription) {
        super(message);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }
}

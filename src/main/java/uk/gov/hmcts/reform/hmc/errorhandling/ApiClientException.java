package uk.gov.hmcts.reform.hmc.errorhandling;

import lombok.Getter;

@Getter
public class ApiClientException extends RuntimeException {

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

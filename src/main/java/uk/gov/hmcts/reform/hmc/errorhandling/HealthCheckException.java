package uk.gov.hmcts.reform.hmc.errorhandling;

import lombok.Getter;

import java.io.Serial;

@Getter
public abstract class HealthCheckException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 5332749868207407114L;

    private final Integer errorCode;
    private final String errorDescription;

    protected HealthCheckException(String message) {
        super(message);
        this.errorCode = null;
        this.errorDescription = null;
    }

    protected HealthCheckException(String message, Integer errorCode, String errorDescription) {
        super(message);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public abstract String getApiName();
}

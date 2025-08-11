package uk.gov.hmcts.reform.hmc.errorhandling;

import java.io.Serial;

public class HealthCheckActiveDirectoryException extends HealthCheckException {

    @Serial
    private static final long serialVersionUID = 3904678669409020986L;

    private static final String API_NAME_ACTIVE_DIRECTORY = "ActiveDirectory";

    public HealthCheckActiveDirectoryException(String message) {
        super(message);
    }

    public HealthCheckActiveDirectoryException(String message, Integer errorCode, String errorDescription) {
        super(message, errorCode, errorDescription);
    }

    @Override
    public String getApiName() {
        return API_NAME_ACTIVE_DIRECTORY;
    }
}

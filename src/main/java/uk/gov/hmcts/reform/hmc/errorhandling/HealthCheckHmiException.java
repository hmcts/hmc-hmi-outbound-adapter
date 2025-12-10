package uk.gov.hmcts.reform.hmc.errorhandling;

import java.io.Serial;

public class HealthCheckHmiException extends HealthCheckException {

    @Serial
    private static final long serialVersionUID = 4344067204953100974L;

    private static final String API_NAME_HMI = "HearingManagementInterface";

    public HealthCheckHmiException(String message) {
        super(message);
    }

    public HealthCheckHmiException(String message, Integer errorCode, String errorDescription) {
        super(message, errorCode, errorDescription);
    }

    @Override
    public String getApiName() {
        return API_NAME_HMI;
    }
}

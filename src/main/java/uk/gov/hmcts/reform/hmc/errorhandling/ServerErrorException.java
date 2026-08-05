package uk.gov.hmcts.reform.hmc.errorhandling;

import lombok.Getter;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;

import java.io.Serial;

@Getter
public class ServerErrorException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 2361403996911877882L;
    private final Integer statusCode;
    private final ErrorDetails errorDetails;

    public ServerErrorException(String message, Integer statusCode, ErrorDetails errorDetails) {
        super(message);
        this.statusCode = statusCode;
        this.errorDetails = errorDetails;
    }

    public Integer deriveErrorCode() {
        if (errorDetails != null) {
            if (errorDetails.getErrorCode() != null) {
                return errorDetails.getErrorCode();
            }
            if (errorDetails.getAuthErrorCodes() != null && !errorDetails.getAuthErrorCodes().isEmpty()) {
                return errorDetails.getAuthErrorCodes().getFirst();
            }
            if (errorDetails.getApiStatusCode() != null) {
                return errorDetails.getApiStatusCode();
            }
        }

        return statusCode;
    }

    public String deriveErrorMessage() {
        if (errorDetails != null) {
            if (errorDetails.getErrorDescription() != null) {
                return errorDetails.getErrorDescription();
            }
            if (errorDetails.getAuthErrorDescription() != null) {
                return errorDetails.getAuthErrorDescription();
            }
            if (errorDetails.getApiErrorMessage() != null) {
                return errorDetails.getApiErrorMessage();
            }
        }

        return getMessage();
    }
}

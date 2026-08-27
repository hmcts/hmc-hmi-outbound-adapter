package uk.gov.hmcts.reform.hmc.errorhandling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ServerErrorExceptionTest {

    private static final String EXCEPTION_MESSAGE = "Exception message";
    private static final String ERROR_DESCRIPTION_ERROR = "Error description";
    private static final String ERROR_DESCRIPTION_AUTH = "Auth error description";
    private static final String ERROR_DESCRIPTION_API = "API error description";

    @DisplayName("shouldDeriveErrorMessageAndCode")
    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("errorDetailsTestData")
    void shouldDeriveErrorMessageAndCode(ErrorDetails errorDetails, Integer expectedCode, String expectedMessage) {
        ServerErrorException exception = new ServerErrorException(EXCEPTION_MESSAGE, 500, errorDetails);

        assertEquals(expectedCode, exception.deriveErrorCode(), "Unexpected error code derived from error details");
        assertEquals(expectedMessage, exception.deriveErrorMessage(),
                     "Unexpected error message derived from error details");
    }

    private static Stream<Arguments> errorDetailsTestData() {
        ErrorDetails errorDetailsError = new ErrorDetails();
        errorDetailsError.setErrorCode(100);
        errorDetailsError.setErrorDescription(ERROR_DESCRIPTION_ERROR);

        ErrorDetails errorDetailsAuth = new ErrorDetails();
        errorDetailsAuth.setAuthErrorCodes(List.of(200, 300));
        errorDetailsAuth.setAuthErrorDescription(ERROR_DESCRIPTION_AUTH);

        ErrorDetails errorDetailsAuthNullErrorCodes = new ErrorDetails();
        errorDetailsAuthNullErrorCodes.setAuthErrorDescription(ERROR_DESCRIPTION_AUTH);

        ErrorDetails errorDetailsAuthEmptyErrorCodes = new ErrorDetails();
        errorDetailsAuthEmptyErrorCodes.setAuthErrorCodes(Collections.emptyList());
        errorDetailsAuthEmptyErrorCodes.setAuthErrorDescription(ERROR_DESCRIPTION_AUTH);

        ErrorDetails errorDetailsApi = new ErrorDetails();
        errorDetailsApi.setApiStatusCode(400);
        errorDetailsApi.setApiErrorMessage(ERROR_DESCRIPTION_API);

        ErrorDetails errorDetailsAll = new ErrorDetails();
        errorDetailsAll.setErrorCode(100);
        errorDetailsAll.setErrorDescription(ERROR_DESCRIPTION_ERROR);
        errorDetailsAll.setAuthErrorCodes(List.of(200, 300));
        errorDetailsAll.setAuthErrorDescription(ERROR_DESCRIPTION_AUTH);
        errorDetailsAll.setApiStatusCode(400);
        errorDetailsAll.setApiErrorMessage(ERROR_DESCRIPTION_API);

        ErrorDetails errorDetailsEmpty = new ErrorDetails();

        return Stream.of(
            arguments(named("ErrorDetails with error fields populated",
                            errorDetailsError),
                      100,
                      ERROR_DESCRIPTION_ERROR),
            arguments(named("ErrorDetails with auth fields populated", errorDetailsAuth), 200, ERROR_DESCRIPTION_AUTH),
            arguments(named("ErrorDetails with auth fields populated, null auth error codes list",
                            errorDetailsAuthNullErrorCodes),
                      500,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("ErrorDetails with auth fields populated, empty auth error codes list",
                            errorDetailsAuthEmptyErrorCodes),
                      500,
                      ERROR_DESCRIPTION_AUTH),
            arguments(named("ErrorDetails with API fields populated", errorDetailsApi), 400, ERROR_DESCRIPTION_API),
            arguments(named("ErrorDetails with all fields populated", errorDetailsAll), 100, ERROR_DESCRIPTION_ERROR),
            arguments(named("ErrorDetails with all fields null ", errorDetailsEmpty), 500, EXCEPTION_MESSAGE),
            arguments(named("Null ErrorDetails", null), 500, EXCEPTION_MESSAGE)
        );
    }
}

package uk.gov.hmcts.reform.hmc.errorHandling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

class AuthenticationExceptionTest {

    @Test
    void shouldCreateAuthenticationExceptionWithMessageAndErrorDetails() {
        // Arrange
        String message = "Authentication failed";
        ErrorDetails errorDetails = new ErrorDetails("ErrorCode", "ErrorMessage");

        // Act
        AuthenticationException exception = new AuthenticationException(message, errorDetails);

        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(errorDetails, exception.getErrorDetails());
    }
}

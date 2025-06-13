package uk.gov.hmcts.reform.hmc.errorhandling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AuthenticationExceptionTest {

    @Test
    void shouldCreateExceptionWithMessageOnly() {
        String message = "Authentication failed";
        uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException exception = new AuthenticationException(message);

        assertEquals(message, exception.getMessage());
    }
}

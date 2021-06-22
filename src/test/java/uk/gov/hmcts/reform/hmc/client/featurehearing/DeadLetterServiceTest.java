package uk.gov.hmcts.reform.hmc.client.featurehearing;

import com.azure.messaging.servicebus.models.DeadLetterOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.errorhandling.DeadLetterService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.hmc.errorhandling.DeadLetterService.APPLICATION_PROCESSING_ERROR;
import static uk.gov.hmcts.reform.hmc.errorhandling.DeadLetterService.MESSAGE_DESERIALIZATION_ERROR;

public class DeadLetterServiceTest {
    private static final String REQUEST_BODY = "original test message";
    private static final String ERROR = "test error message";
    private DeadLetterOptions expected;

    @InjectMocks
    private DeadLetterService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        expected = new DeadLetterOptions();
        expected.setDeadLetterErrorDescription("DeadLetterMessage(originalMessage=" + REQUEST_BODY
                                                   + ", errorDescription=" + ERROR + ")");
    }

    @Test
    void shouldHandleApplicationError() {
        expected.setDeadLetterReason(APPLICATION_PROCESSING_ERROR);
        DeadLetterOptions result = service.handleApplicationError(REQUEST_BODY, ERROR);
        assertEquals(expected.getDeadLetterErrorDescription(), result.getDeadLetterErrorDescription());
        assertEquals(expected.getDeadLetterReason(), result.getDeadLetterReason());
    }

    @Test
    void shouldHandleParsingError() {
        expected.setDeadLetterReason(MESSAGE_DESERIALIZATION_ERROR);
        DeadLetterOptions result = service.handleParsingError(REQUEST_BODY, ERROR);
        assertEquals(expected.getDeadLetterErrorDescription(), result.getDeadLetterErrorDescription());
        assertEquals(expected.getDeadLetterReason(), result.getDeadLetterReason());
    }
}

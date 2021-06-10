package uk.gov.hmcts.reform.hmc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import static org.mockito.Mockito.when;

public class MessageReceiverConfigurationTest {

    @InjectMocks
    private MessageReceiverConfiguration messageReceiverConfiguration;

    @Mock
    private ApplicationParams applicationParams;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldConnectToQueue() throws InterruptedException {
        when(applicationParams.getConnectionString()).thenReturn(
            "Endpoint=sb://namespacename.servicebus.windows.net/"
                + ";SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=key");
        when(applicationParams.getQueueName()).thenReturn("queueName");
        when(applicationParams.getWaitToRetryTime()).thenReturn("1");
        messageReceiverConfiguration.receiveMessages();
    }
}

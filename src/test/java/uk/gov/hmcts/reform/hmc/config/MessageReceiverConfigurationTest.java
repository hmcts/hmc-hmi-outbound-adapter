package uk.gov.hmcts.reform.hmc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.errorhandling.HearingManagementInterfaceErrorHandler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MessageReceiverConfigurationTest {

    @Mock
    private MessageReceiverConfiguration messageReceiverConfigurationMock;

    @Mock
    private HearingManagementInterfaceErrorHandler handler;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Mock
    private HearingManagementInterfaceApiClient hmiClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Disabled
    @Test
    void shouldConnectToQueue() throws InterruptedException {
        when(applicationParams.getConnectionString()).thenReturn(
            "Endpoint=sb://namespacename.servicebus.windows.net/"
                + ";SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=key");
        when(applicationParams.getQueueName()).thenReturn("queueName");
        when(applicationParams.getWaitToRetryTime()).thenReturn("1");
        MessageReceiverConfiguration messageReceiverConfiguration
            = new MessageReceiverConfiguration(applicationParams,
                                               activeDirectoryApiClient,
                                               hmiClient, handler
        );
        messageReceiverConfiguration.run();
    }

    @Disabled
    @Test
    void shouldConnectToQueue1() throws InterruptedException {

        InterruptedException exception = assertThrows(InterruptedException.class, () -> {
            messageReceiverConfigurationMock.run();
        });
        String expectedMessage = "For input string";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }
}

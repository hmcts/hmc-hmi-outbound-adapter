package uk.gov.hmcts.reform.hmc.client.featurehearing;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceHeadersInterceptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UUID.class})
public class HearingManagementInterfaceHeadersInterceptorTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2021-06-10T04:00:00.08Z"), ZoneOffset.UTC);
    private static MockedStatic<UUID> mockedUuid;
    private static final String SOURCE_SYSTEM = "SOURCE_SYSTEM";
    private static final String DESTINATION_SYSTEM = "DESTINATION_SYSTEM";
    private static final String TEST_TOKEN = "test-token";

    @InjectMocks
    HearingManagementInterfaceHeadersInterceptor hearingManagementInterfaceHeadersInterceptor;

    @Mock
    ApplicationParams applicationParams;
    private RequestTemplate template;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedUuid = mockStatic(UUID.class);
        template = new RequestTemplate();
        template.header(AUTHORIZATION,TEST_TOKEN);
        hearingManagementInterfaceHeadersInterceptor = new
            HearingManagementInterfaceHeadersInterceptor(applicationParams, fixedClock);
        given(applicationParams.getSourceSystem()).willReturn(SOURCE_SYSTEM);
        given(applicationParams.getDestinationSystem()).willReturn(DESTINATION_SYSTEM);
    }

    @AfterEach
    public void close() {
        mockedUuid.close();
    }

    @Test
    @DisplayName("Headers should be added if not present")
    void shouldApplyHeaders() {
        UUID transactionId = UUID.randomUUID();
        when(UUID.randomUUID()).thenReturn(transactionId);

        hearingManagementInterfaceHeadersInterceptor.apply(template);

        assertThat(template.headers().get(AUTHORIZATION)).containsOnly(TEST_TOKEN);
        assertThat(template.headers().get("transactionIdHMCTS")).containsOnly(String.valueOf(transactionId));
        assertThat(template.headers().get("Source-System")).containsOnly(SOURCE_SYSTEM);
        assertThat(template.headers().get("Destination-System")).containsOnly(DESTINATION_SYSTEM);
        assertThat(template.headers().get("Request-Created-At")).containsOnly(fixedClock.instant().toString());
    }

    @Test
    @DisplayName("Headers shouldn't be overridden if present")
    void shouldNotOverrideHeaders() {
        UUID transactionId = UUID.randomUUID();
        when(UUID.randomUUID()).thenReturn(transactionId);

        template.header("Request-Created-At", "test");
        template.header("Destination-System", "test");

        hearingManagementInterfaceHeadersInterceptor.apply(template);

        verify(applicationParams, times(0)).getDestinationSystem();
        assertThat(template.headers().get("Request-Created-At")).containsOnly("test");
        assertThat(template.headers().get(AUTHORIZATION)).containsOnly(TEST_TOKEN);
        assertThat(template.headers().get("transactionIdHMCTS")).containsOnly(String.valueOf(transactionId));
        assertThat(template.headers().get("Source-System")).containsOnly(SOURCE_SYSTEM);
    }
}

package uk.gov.hmcts.reform.hmc;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.contract.wiremock.WireMockConfigurationCustomizer;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.hmc.config.MessageSenderToTopicConfiguration;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock
@AutoConfigureMockMvc
@ActiveProfiles("itest")
public class BaseTest implements WireMockConfigurationCustomizer {

    @MockBean
    protected MessageSenderToTopicConfiguration messageSenderToTopicConfiguration;

    @Value("${wiremock.server.port}")
    protected Integer wiremockPort;

    @Override
    public void customize(WireMockConfiguration config) {
        config.extensions(new WiremockFixtures.ConnectionClosedTransformer());
        config.port(wiremockPort);
    }

}

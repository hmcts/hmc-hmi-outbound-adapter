package uk.gov.hmcts.reform.hmc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import uk.gov.hmcts.reform.hmc.config.MessageReceiverConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
@EnableFeignClients
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class Application {

    private static ApplicationParams applicationParams;
    private final MessageReceiverConfiguration messageReceiverConfiguration;

    public Application(MessageReceiverConfiguration messageReceiverConfiguration, ApplicationParams applicationParams) {
        this.applicationParams = applicationParams;
        this.messageReceiverConfiguration = messageReceiverConfiguration;
    }

    public static void main(final String[] args) throws InterruptedException {
        SpringApplication.run(Application.class, args);
        new MessageReceiverConfiguration(applicationParams).receiveMessages();
    }
}

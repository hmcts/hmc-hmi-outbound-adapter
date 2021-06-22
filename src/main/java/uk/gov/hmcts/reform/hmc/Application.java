package uk.gov.hmcts.reform.hmc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import uk.gov.hmcts.reform.hmc.config.MessageReceiverConfiguration;
import uk.gov.hmcts.reform.hmc.errorhandling.HearingManagementInterfaceErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.FutureHearingRepository;

import java.time.Clock;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
@EnableFeignClients
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class Application {

    private static FutureHearingRepository repository;
    private static HearingManagementInterfaceErrorHandler handler;
    private static ApplicationParams applicationParams;
    private final MessageReceiverConfiguration messageReceiverConfiguration;

    public Application(MessageReceiverConfiguration messageReceiverConfiguration, ApplicationParams applicationParams,
                       HearingManagementInterfaceErrorHandler handler, FutureHearingRepository repository) {
        this.repository = repository;
        this.handler = handler;
        this.applicationParams = applicationParams;
        this.messageReceiverConfiguration = messageReceiverConfiguration;
    }

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
        new MessageReceiverConfiguration(applicationParams, handler, repository).receiveMessages();
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}

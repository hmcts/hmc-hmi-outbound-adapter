package uk.gov.hmcts.reform.hmc;

import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.gov.hmcts.reform.hmc.config.MessageReceiverConfiguration;

import java.time.Clock;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, it is not a utility class
public class Application {
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    static LazyInitializationExcludeFilter lazyInitExcludeFilter() {
        return LazyInitializationExcludeFilter.forBeanTypes(
                MessageReceiverConfiguration.class
        );
    }
}

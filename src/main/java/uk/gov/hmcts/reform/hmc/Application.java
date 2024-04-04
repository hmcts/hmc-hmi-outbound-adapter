package uk.gov.hmcts.reform.hmc;

import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
@EnableFeignClients
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, it is not a utility class
public class Application {
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

}

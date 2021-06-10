package uk.gov.hmcts.reform.hmc;

import org.springframework.beans.factory.annotation.Value;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class ApplicationParams {

    @Value("${spring.jms.servicebus.queue-name}")
    private String queueName;

    @Value("${spring.jms.servicebus.connection-string}")
    private String connectionString;

    @Value("${spring.jms.servicebus.wait-to-retry-time}")
    private String waitToRetryTime;

    public String getQueueName() {
        return queueName;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getWaitToRetryTime() {
        return waitToRetryTime;
    }
}

package uk.gov.hmcts.reform.hmc;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.reform.hmc.exceptions.ServiceException;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@Getter
@Named
@Singleton
public class ApplicationParams {

    @Value("${fh.ad.client-id}")
    private String clientId;

    @Value("${fh.ad.client-secret}")
    private String clientSecret;

    @Value("${fh.ad.scope}")
    private String scope;

    @Value("${fh.ad.grant-type}")
    private String grantType;

    @Value("${fh.hmi.source-system}")
    private String sourceSystem;

    @Value("${fh.hmi.destination-system}")
    private String destinationSystem;

    @Value("${azure.jms.servicebus.outbound-queue-name}")
    private String outboundQueueName;

    @Value("${azure.jms.servicebus.inbound-queue-name}")
    private String inboundQueueName;

    @Value("${azure.jms.servicebus.outbound-connection-string}")
    private String outboundConnectionString;

    @Value("${azure.jms.servicebus.inbound-connection-string}")
    private String inboundConnectionString;

    @Value("${role.assignment.api.host}")
    private String roleAssignmentServiceHost;

    @Value("${azure.jms.servicebus.exponential-multiplier}")
    private String exponentialMultiplier;

    @Value("${azure.jms.servicebus.max-retry-attempts}")
    private int maxRetryAttempts;

    public static String encode(final String stringToEncode) {
        try {
            return URLEncoder.encode(stringToEncode, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    public String roleAssignmentBaseUrl() {
        return roleAssignmentServiceHost + "/am/role-assignments";
    }

    public String amGetRoleAssignmentsUrl() {
        return roleAssignmentBaseUrl() + "/actors/{uid}";
    }
}

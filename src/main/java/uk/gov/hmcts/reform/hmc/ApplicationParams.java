package uk.gov.hmcts.reform.hmc;

import org.springframework.beans.factory.annotation.Value;

import javax.inject.Named;
import javax.inject.Singleton;

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

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getGrantType() {
        return grantType;
    }

    public String getScope() {
        return scope;
    }
}

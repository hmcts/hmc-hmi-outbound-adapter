package uk.gov.hmcts.reform.hmc.client.futurehearing;

import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import org.springframework.context.annotation.Bean;

public class FutureHearingApiClientConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new FutureHearingErrorDecoder();
    }

    // Added to fix issue where 401 feign responses would omit body
    @Bean
    public OkHttpClient client() {
        return new OkHttpClient();
    }
}

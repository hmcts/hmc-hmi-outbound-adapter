package uk.gov.hmcts.reform.hmc.client.futurehearing;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

public class FutureHearingErrorDecoder implements ErrorDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(FutureHearingErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() != null) {
            LOG.error(response.body().toString());
        } else {
            LOG.error(response.reason());
        }
        switch (response.status()) {
            case 400:
                return new AuthenticationException("Missing one or more required parameters");
            case 401:
                return new AuthenticationException("Invalid secrets");
            default:
                return new Exception(response.reason());
        }
    }
}

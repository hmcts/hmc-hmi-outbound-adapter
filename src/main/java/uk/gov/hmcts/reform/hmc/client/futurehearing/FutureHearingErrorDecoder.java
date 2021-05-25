package uk.gov.hmcts.reform.hmc.client.futurehearing;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

public class FutureHearingErrorDecoder implements ErrorDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(FutureHearingErrorDecoder.class);
    public static final String INVALID_REQUEST = "Missing or invalid request parameters";
    public static final String INVALID_SECRET = "Authentication error";

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() != null) {
            LOG.error(response.body().toString());
        } else {
            LOG.error(response.reason());
        }
        switch (response.status()) {
            case 400:
                return new AuthenticationException(INVALID_REQUEST);
            case 401:
                return new AuthenticationException(INVALID_SECRET);
            default:
                return new AuthenticationException(response.reason());
        }
    }
}

package uk.gov.hmcts.reform.rsecheck;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;

public class FutureHearingErrorDecoderTest {

    private String methodKey = null;
    private Response response;

    @InjectMocks
    private FutureHearingErrorDecoder futureHearingErrorDecoder;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldThrowAuthenticationExceptionWith400Error() {
        response = Response.builder()
            .status(400)
            .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .reason("Test")
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);
        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertThat(exception.getMessage().equals(INVALID_REQUEST));

    }

    @Test
    public void shouldThrowAuthenticationExceptionWith401Error() {
        response = Response.builder()
            .status(401)
            .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .reason("Test")
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);
        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertThat(exception.getMessage().equals(INVALID_SECRET));

    }

    @Test
    public void shouldThrowAuthenticationExceptionWith500Error() {
        response = Response.builder()
            .status(500)
            .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .reason("Test")
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);
        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertThat(exception.getMessage().equals("Test"));

    }
}

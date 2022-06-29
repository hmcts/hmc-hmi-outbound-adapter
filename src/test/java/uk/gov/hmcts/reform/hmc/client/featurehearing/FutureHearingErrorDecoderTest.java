package uk.gov.hmcts.reform.hmc.client.featurehearing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.REQUEST_NOT_FOUND;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;

class FutureHearingErrorDecoderTest {

    private String methodKey = null;
    private Response response;
    private byte[] byteArrray;
    private static final String INPUT_STRING = "{\n"
        + "    \"errCode\": \"1000\",\n"
        + "    \"errorDesc\": \"'300' is not a valid value for 'caseCourt.locationId'\",\n"
        + "    \"errorLinkId\": null,\n"
        + "    \"exception\": null\n"
        + "}";
    private static final String EXPECTED_ERROR = "Response from FH failed with HTTP code %s, error code 1000, "
        + "error message ''300' is not a valid value for 'caseCourt.locationId''";
    private RequestTemplate template;

    private final Logger logger = (Logger) LoggerFactory.getLogger(FutureHearingErrorDecoder.class);

    @InjectMocks
    private FutureHearingErrorDecoder futureHearingErrorDecoder;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        byteArrray = INPUT_STRING.getBytes();
        logger.setLevel(Level.INFO);
    }

    @Test
    void shouldThrowAuthenticationExceptionWith400Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = Response.builder()
            .body(byteArrray)
            .status(400)
            .request(Request.create(HttpMethod.POST, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        assertEquals(INVALID_REQUEST, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 400), logsList.get(0)
            .getMessage());
    }

    @Test
    void shouldThrowAuthenticationExceptionWith401Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = Response.builder()
            .body(byteArrray)
            .status(401)
            .request(Request.create(HttpMethod.POST, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertEquals(INVALID_SECRET, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 401), logsList.get(0)
            .getMessage());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWith404Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = Response.builder()
            .body(byteArrray)
            .status(404)
            .request(Request.create(HttpMethod.PUT, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(ResourceNotFoundException.class);
        assertEquals(REQUEST_NOT_FOUND, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 404), logsList.get(0)
            .getMessage());
    }

    @Test
    void shouldThrowAuthenticationExceptionWith500Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = Response.builder()
            .body(byteArrray)
            .status(500)
            .request(Request.create(HttpMethod.POST, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertEquals(SERVER_ERROR, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 500), logsList.get(0)
            .getMessage());
    }

    @Test
    void shouldLogPayloadsInDebug() {

        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = Response.builder()
            .body(byteArrray)
            .status(400)
            .request(Request.create(HttpMethod.POST, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        assertEquals(INVALID_REQUEST, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 400), logsList.get(0)
            .getMessage());
        assertEquals(Level.DEBUG, logsList.get(1)
            .getLevel());
        assertEquals("Request to FH - URL: /api, Method: POST, Payload: n/a", logsList.get(1)
            .getMessage());
        assertEquals(Level.DEBUG, logsList.get(2)
            .getLevel());
        assertEquals("Error payload from FH (HTTP 400): " + INPUT_STRING, logsList.get(2)
            .getMessage());
    }
}

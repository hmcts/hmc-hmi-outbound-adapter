package uk.gov.hmcts.reform.rsecheck;

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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;

public class FutureHearingErrorDecoderTest {

    private String methodKey = null;
    private Response response;
    private byte[] byteArrray;
    private String inputString = "This response message should be logged";
    private RequestTemplate template;

    @InjectMocks
    private FutureHearingErrorDecoder futureHearingErrorDecoder;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        byteArrray = inputString.getBytes();
    }

    @Test
    public void shouldThrowAuthenticationExceptionWith400Error() {

        Logger logger = (Logger) LoggerFactory.getLogger(FutureHearingErrorDecoder.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = Response.builder()
            .body(byteArrray)
            .status(400)
            .request(Request.create(HttpMethod.POST, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertThat(exception.getMessage().equals(INVALID_REQUEST));
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(logsList.size(), 1);
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(inputString, logsList.get(0)
            .getMessage());
    }

    @Test
    public void shouldThrowAuthenticationExceptionWith401Error() {

        Logger logger = (Logger) LoggerFactory.getLogger(FutureHearingErrorDecoder.class);
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
        assertThat(exception.getMessage().equals(INVALID_SECRET));
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(logsList.size(), 1);
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(inputString, logsList.get(0)
            .getMessage());

    }

    @Test
    public void shouldThrowAuthenticationExceptionWith500Error() {

        Logger logger = (Logger) LoggerFactory.getLogger(FutureHearingErrorDecoder.class);
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
        assertThat(exception.getMessage().equals(SERVER_ERROR));
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(logsList.size(), 1);
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(inputString, logsList.get(0)
            .getMessage());
    }
}

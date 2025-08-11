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
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.REQUEST_NOT_FOUND;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;

class FutureHearingErrorDecoderTest {

    private String methodKey = null;
    private Response response;
    private byte[] byteArray;
    private static final String INPUT_STRING = """
        {
            "errCode": "1000",
            "errorDesc": "'300' is not a valid value for 'caseCourt.locationId'",
            "errorLinkId": null,
            "exception": null,
            "statusCode": null,
            "message": null
        }""";
    private static final String EXPECTED_ERROR = "Response from FH failed with HTTP code %s, error code 1000, "
        + "error message ''300' is not a valid value for 'caseCourt.locationId'', "
        + "AuthErrorCode null, AuthErrorMessage 'null', "
        + "ApiStatusCode null, ApiErrorMessage 'null'";
    private RequestTemplate template;

    private final Logger logger = (Logger) LoggerFactory.getLogger(FutureHearingErrorDecoder.class);

    @InjectMocks
    private FutureHearingErrorDecoder futureHearingErrorDecoder;

    @BeforeEach
     void setUp() {
        MockitoAnnotations.openMocks(this);
        byteArray = INPUT_STRING.getBytes();
        logger.setLevel(Level.INFO);
    }

    @Test
    void shouldThrowAuthenticationExceptionWith400Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = createResponse(byteArray, 400, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        assertEquals(INVALID_REQUEST, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        ILoggingEvent logEvent = logsList.getFirst();
        assertEquals(Level.ERROR, logEvent.getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 400), logEvent.getFormattedMessage());
    }

    @Test
    void shouldThrowAuthenticationExceptionWith401Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = createResponse(byteArray, 401, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertEquals(INVALID_SECRET, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        ILoggingEvent logEvent = logsList.getFirst();
        assertEquals(Level.ERROR, logEvent.getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 401), logEvent.getFormattedMessage());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWith404Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = createResponse(byteArray, 404, HttpMethod.PUT);

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(ResourceNotFoundException.class);
        assertEquals(REQUEST_NOT_FOUND, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        ILoggingEvent logEvent = logsList.getFirst();
        assertEquals(Level.ERROR, logEvent.getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 404), logEvent.getFormattedMessage());
    }

    @Test
    void shouldThrowAuthenticationExceptionWith500Error() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = createResponse(byteArray, 500, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertEquals(SERVER_ERROR, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        ILoggingEvent logEvent = logsList.getFirst();
        assertEquals(Level.ERROR, logEvent.getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 500), logEvent.getFormattedMessage());
    }

    @Test
    void shouldPopulateApiErrorFields() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        String apiError = """
            {
                "errCode": null,
                "errorDesc": null,
                "errLinkId": null,
                "statusCode": 400,
                "message": "Missing/Invalid Header Source-System"
            }
            """;
        byte[] apiErrorByteArray = apiError.getBytes(StandardCharsets.UTF_8);

        response = createResponse(apiErrorByteArray, 400, HttpMethod.GET);

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        BadFutureHearingRequestException badRequestException = (BadFutureHearingRequestException) exception;
        assertEquals(INVALID_REQUEST, badRequestException.getMessage());

        ErrorDetails errorDetails = badRequestException.getErrorDetails();
        assertNotNull(errorDetails, "Exception error details should not be null");
        assertEquals(400, errorDetails.getApiStatusCode(), "Error details has unexpected API status code");
        assertEquals("Missing/Invalid Header Source-System",
                     errorDetails.getApiErrorMessage(),
                     "Error details has unexpected API message");

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        ILoggingEvent logEvent = logsList.getFirst();
        assertEquals(Level.ERROR, logEvent.getLevel(), "Log event has unexpected level");
        assertEquals("Response from FH failed with HTTP code 400, error code null, error message 'null', "
                         + "AuthErrorCode null, AuthErrorMessage 'null', "
                         + "ApiStatusCode 400, ApiErrorMessage 'Missing/Invalid Header Source-System'",
                     logEvent.getFormattedMessage(),
                     "Log event has unexpected message");

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldLogPayloadsInDebug() {

        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        response = createResponse(byteArray, 400, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(methodKey, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        assertEquals(INVALID_REQUEST, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertEquals(Level.ERROR, logsList.get(0)
            .getLevel());
        assertEquals(String.format(EXPECTED_ERROR, 400), logsList.get(0)
            .getFormattedMessage());
        assertEquals(Level.DEBUG, logsList.get(1)
            .getLevel());
        assertEquals("Request to FH - URL: /api, Method: POST, Payload: n/a", logsList.get(1)
            .getFormattedMessage());
        assertEquals(Level.DEBUG, logsList.get(2)
            .getLevel());
        assertEquals("Error payload from FH (HTTP 400): " + INPUT_STRING, logsList.get(2)
            .getFormattedMessage());
    }

    @Test
    void shouldReturnEmptyOptionalWhenIoExceptionOccurs() {
        Response.Body body = new Response.Body() {
            @Override
            public void close() throws IOException {
                // This method is intentionally left empty as no resources need to be closed.
            }

            @Override
            public Integer length() {
                return null;
            }

            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public InputStream asInputStream() throws IOException {
                throw new IOException("Simulated IO error");
            }

            @Override
            public Reader asReader() throws IOException {
                throw new IOException("Simulated IO error");
            }

            @Override
            public Reader asReader(Charset charset) throws IOException {
                return null;
            }
        };

        response = Response.builder()
            .body(body)
            .status(500)
            .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8, null))
            .build();

        Optional<Object> result = new FutureHearingErrorDecoder().getResponseBody(response, Object.class);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldLogErrorWhenIoExceptionOccursWhileReadingResponseBody() {
        logger.setLevel(Level.ERROR);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response.Body body = new Response.Body() {
            @Override
            public void close() throws IOException {
                // This method is intentionally left empty as no resources need to be closed.
            }

            @Override
            public Integer length() {
                return null;
            }

            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public InputStream asInputStream() throws IOException {
                throw new IOException("Simulated IO error");
            }

            @Override
            public Reader asReader() throws IOException {
                throw new IOException("Simulated IO error");
            }

            @Override
            public Reader asReader(Charset charset) throws IOException {
                return null;
            }
        };

        response = Response.builder()
            .body(body)
            .status(500)
            .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8, null))
            .build();

        new FutureHearingErrorDecoder().getResponseBody(response, Object.class);

        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.stream().anyMatch(
            log -> log.getLevel() == Level.ERROR && log.getMessage().contains("Response from FH failed with error code")
        )).isTrue();
    }

    private Response createResponse(byte[] body, int status, HttpMethod httpMethod) {
        return Response.builder()
            .body(body)
            .status(status)
            .request(Request.create(httpMethod, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();
    }
}

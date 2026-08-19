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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ErrorDetails;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder;
import uk.gov.hmcts.reform.hmc.errorhandling.ApiClientException;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.REQUEST_NOT_FOUND;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.SERVER_ERROR;

@ExtendWith(MockitoExtension.class)
class FutureHearingErrorDecoderTest {

    private static final String METHOD_KEY = null;
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

    private byte[] byteArray;

    private final Logger logger = (Logger) LoggerFactory.getLogger(FutureHearingErrorDecoder.class);

    @Mock
    private Response mockResponse;

    @Mock
    private Response.Body mockResponseBody;

    private FutureHearingErrorDecoder futureHearingErrorDecoder;

    @BeforeEach
     void setUp() {
        futureHearingErrorDecoder = new FutureHearingErrorDecoder();
        byteArray = INPUT_STRING.getBytes();
        logger.setLevel(Level.INFO);
    }

    @Test
    void shouldThrowBadFutureHearingRequestExceptionWith400Error() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response response = createResponse(byteArray, 400, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        assertEquals(INVALID_REQUEST, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertLogEntry(logsList.getFirst(), Level.ERROR, String.format(EXPECTED_ERROR, 400));

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldThrowAuthenticationExceptionWith401Error() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response response = createResponse(byteArray, 401, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertEquals(INVALID_SECRET, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertLogEntry(logsList.getFirst(), Level.ERROR, String.format(EXPECTED_ERROR, 401));

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWith404Error() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response response = createResponse(byteArray, 404, HttpMethod.PUT);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(ResourceNotFoundException.class);
        assertEquals(REQUEST_NOT_FOUND, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertLogEntry(logsList.getFirst(), Level.ERROR, String.format(EXPECTED_ERROR, 404));

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldThrowAuthenticationExceptionWith500Error() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response response = createResponse(byteArray, 500, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);
        assertEquals(SERVER_ERROR, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertLogEntry(logsList.getFirst(), Level.ERROR, String.format(EXPECTED_ERROR, 500));

        logger.detachAndStopAllAppenders();
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

        Response response = createResponse(apiErrorByteArray, 400, HttpMethod.GET);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

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

        assertLogEntry(logsList.getFirst(),
                       Level.ERROR,
                       "Response from FH failed with HTTP code 400, error code null, error message 'null', "
                           + "AuthErrorCode null, AuthErrorMessage 'null', "
                           + "ApiStatusCode 400, ApiErrorMessage 'Missing/Invalid Header Source-System'");

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldLogPayloadsInDebug() {
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response response = createResponse(byteArray, 400, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(BadFutureHearingRequestException.class);
        assertEquals(INVALID_REQUEST, exception.getMessage());
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertLogEntry(logsList.get(0), Level.ERROR, String.format(EXPECTED_ERROR, 400));
        assertLogEntry(logsList.get(1), Level.DEBUG, "Request to FH - URL: /api, Method: POST, Payload: n/a");
        assertLogEntry(logsList.get(2), Level.DEBUG, "Error payload from FH (HTTP 400): " + INPUT_STRING);

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldLogNonNullRequestPayloadInDebug() {
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        String responseBody = """
            {
                "statusCode": 401,
                "message": "Unauthorised"
            }""";

        byte[] requestBody = "RequestBody".getBytes(StandardCharsets.UTF_8);

        Response response = Response.builder()
            .body(responseBody.getBytes(StandardCharsets.UTF_8))
            .status(401)
            .request(Request.create(HttpMethod.POST, "/api", Collections.emptyMap(), requestBody, Util.UTF_8, null))
            .build();

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(AuthenticationException.class);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertLogEntry(logsList.get(0),
                       Level.ERROR,
                       "Response from FH failed with HTTP code 401, error code null, "
                           + "error message 'null', AuthErrorCode null, AuthErrorMessage 'null', "
                           + "ApiStatusCode 401, ApiErrorMessage 'Unauthorised'");
        assertLogEntry(logsList.get(1), Level.DEBUG, "Request to FH - URL: /api, Method: POST, Payload: RequestBody");
        assertLogEntry(logsList.get(2), Level.DEBUG, "Error payload from FH (HTTP 401): " + responseBody);

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldLogNullResponsePayloadInDebug() {
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Response response = createResponse(null, 404, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        assertThat(exception).isInstanceOf(ResourceNotFoundException.class);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertLogEntry(logsList.get(0),
                       Level.ERROR,
                       "Response from FH failed with HTTP code 404, error code null, error message 'null', "
                           + "AuthErrorCode null, AuthErrorMessage 'null', ApiStatusCode null, ApiErrorMessage 'null'");
        assertLogEntry(logsList.get(1), Level.DEBUG, "Request to FH - URL: /api, Method: POST, Payload: n/a");
        assertLogEntry(logsList.get(2), Level.DEBUG, "Error payload from FH (HTTP 404): n/a");

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldThrowApiClientExceptionWhenIoExceptionOccurs() throws IOException {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        when(mockResponseBody.asInputStream()).thenThrow(new IOException("Simulated IO error"));
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponse.status()).thenReturn(500);

        // noinspection ThrowableNotThrown
        ApiClientException apiClientException =
            assertThrows(ApiClientException.class,
                         () -> futureHearingErrorDecoder.decode(METHOD_KEY, mockResponse),
                         "ApiClientException should be thrown");

        assertEquals(SERVER_ERROR,
                     apiClientException.getMessage(),
                     "ApiClientException has unexpected message");
        assertEquals(500, apiClientException.getErrorCode(), "ApiClientException has unexpected error code");
        assertNull(apiClientException.getErrorDescription(), "ApiClientException error description should be null");

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size(), "Unexpected number of messages in log");

        assertLogEntry(logsList.getFirst(),
                       Level.ERROR,
                       "Response from FH failed with error code 500, unable to read response body");

        logger.detachAndStopAllAppenders();

        verify(mockResponse, times(2)).status();
        verify(mockResponse, times(2)).body();
        verify(mockResponseBody).asInputStream();
    }

    @Test
    void shouldHaveNullErrorDetailsFieldsWhenResponseBodyNull() {
        Response response = createResponse(null, 400, HttpMethod.GET);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        BadFutureHearingRequestException badFutureHearingRequestException =
            assertInstanceOf(BadFutureHearingRequestException.class,
                             exception,
                             "BadFutureHearingRequestException should be returned");

        assertEquals(INVALID_REQUEST,
                     badFutureHearingRequestException.getMessage(),
                     "BadFutureHearingRequestException has unexpected message");

        ErrorDetails errorDetails = badFutureHearingRequestException.getErrorDetails();
        assertNotNull(errorDetails, "ErrorDetails should not be null");
        assertErrorDetailsFieldsNull(errorDetails);
    }

    @Test
    void shouldHaveNullErrorDetailsFieldsWhenResponseBodyHasDifferentFields() {
        byte[] responseBody = """
            {
                "differentField": "differentFieldValue"
            }""".getBytes(StandardCharsets.UTF_8);
        Response response = createResponse(responseBody, 401, HttpMethod.GET);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        AuthenticationException authenticationException =
            assertInstanceOf(AuthenticationException.class, exception, "AuthenticationException should be returned");

        assertEquals(INVALID_SECRET,
                     authenticationException.getMessage(),
                     "AuthenticationException has unexpected message");

        ErrorDetails errorDetails = authenticationException.getErrorDetails();
        assertNotNull(errorDetails, "ErrorDetails should not be null");
        assertErrorDetailsFieldsNull(errorDetails);
    }

    @ParameterizedTest
    @MethodSource("nonJsonResponseBody")
    void shouldThrowApiClientExceptionWhenResponseBodyNotJson(String responseBody) {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        byte[] responseBodyByteArray = responseBody.getBytes(StandardCharsets.UTF_8);
        Response response = createResponse(responseBodyByteArray, 500, HttpMethod.GET);

        // noinspection ThrowableNotThrown
        ApiClientException apiClientException =
            assertThrows(ApiClientException.class,
                         () -> futureHearingErrorDecoder.decode(METHOD_KEY, response),
                         "ApiClientException should be thrown");

        assertEquals(500, apiClientException.getErrorCode(), "ApiClientException has unexpected error code");
        assertEquals(responseBody,
                     apiClientException.getErrorDescription(),
                     "ApiClientException has unexpected error description");

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size(), "Unexpected number of messages in log");

        assertLogEntry(logsList.getFirst(),
                       Level.ERROR,
                       "Response from FH failed with error code 500, error message " + responseBody);

        logger.detachAndStopAllAppenders();
    }

    @Test
    void shouldThrowApiClientExceptionWhenResponseBodyIsWordNull() {
        String responseBody = "null";
        byte[] responseBodyByteArray = responseBody.getBytes(StandardCharsets.UTF_8);
        Response response = createResponse(responseBodyByteArray, 400, HttpMethod.GET);

        // noinspection ThrowableNotThrown
        ApiClientException apiClientException =
            assertThrows(ApiClientException.class,
                         () -> futureHearingErrorDecoder.decode(METHOD_KEY, response),
                         "ApiClientException should be thrown");

        assertEquals(400, apiClientException.getErrorCode(), "ApiClientException has unexpected error code");
        assertEquals(responseBody,
                     apiClientException.getErrorDescription(),
                     "ApiClientException has unexpected error description");
    }

    @Test
    void shouldPopulateAuthErrorFields() {
        byte[] body = """
            {
                "error_codes": [100, 200],
                "error_description": "Auth error"
            }""".getBytes(StandardCharsets.UTF_8);

        Response response = createResponse(body, 401, HttpMethod.POST);

        Exception exception = futureHearingErrorDecoder.decode(METHOD_KEY, response);

        AuthenticationException authenticationException =
            assertInstanceOf(AuthenticationException.class, exception, "AuthenticationException should be returned");

        ErrorDetails errorDetails = authenticationException.getErrorDetails();

        assertNull(errorDetails.getErrorCode(), "Error code should be null");
        assertNull(errorDetails.getErrorDescription(), "Error description should be null");

        List<Integer> authErrorCodes = errorDetails.getAuthErrorCodes();
        assertNotNull(authErrorCodes, "Auth error codes should not be null");
        assertEquals(2, authErrorCodes.size(), "Auth error codes contains an unexpected number of entries");
        for (Integer expectedAuthErrorCode : List.of(100, 200)) {
            assertTrue(authErrorCodes.contains(expectedAuthErrorCode),
                       "Auth error codes should contain " + expectedAuthErrorCode);
        }
        assertEquals("Auth error", errorDetails.getAuthErrorDescription(), "Unexpected Auth error value");

        assertNull(errorDetails.getApiStatusCode(), "API Status code should be null");
        assertNull(errorDetails.getApiErrorMessage(), "API error message should be null");
    }

    private static Stream<Arguments> nonJsonResponseBody() {
        return Stream.of(
            arguments(""),
            arguments("""
                      <html>
                          <head>
                              <title>500 Internal Server Error</title>
                          </head>
                          <body>
                              <h1>Internal Server Error</h1>
                          </body>
                      </html>""")
        );
    }

    private void assertLogEntry(ILoggingEvent logEvent, Level expectedLevel, String expectedMessage) {
        assertEquals(expectedLevel, logEvent.getLevel(), "Log entry has unexpected level");
        assertEquals(expectedMessage, logEvent.getFormattedMessage(), "Log entry has unexpected message");
    }

    private void assertErrorDetailsFieldsNull(ErrorDetails errorDetails) {
        assertNull(errorDetails.getErrorCode(), "Error code should be null");
        assertNull(errorDetails.getErrorDescription(), "Error description should be null");
        assertNull(errorDetails.getAuthErrorCodes(), "Auth error codes should be null");
        assertNull(errorDetails.getAuthErrorDescription(), "Auth error description should be null");
        assertNull(errorDetails.getApiStatusCode(), "API Status code should be null");
        assertNull(errorDetails.getApiErrorMessage(), "API error message should be null");
    }

    private Response createResponse(byte[] body, int status, HttpMethod httpMethod) {
        RequestTemplate template = new RequestTemplate();
        return Response.builder()
            .body(body)
            .status(status)
            .request(Request.create(httpMethod, "/api", Collections.emptyMap(), null, Util.UTF_8, template))
            .build();
    }
}

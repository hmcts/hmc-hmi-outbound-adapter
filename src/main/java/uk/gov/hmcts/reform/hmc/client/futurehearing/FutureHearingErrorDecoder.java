package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;
import uk.gov.hmcts.reform.hmc.errorhandling.BadFutureHearingRequestException;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class FutureHearingErrorDecoder implements ErrorDecoder {
    public static final String INVALID_REQUEST = "Missing or invalid request parameters";
    public static final String INVALID_SECRET = "Authentication error";
    public static final String SERVER_ERROR = "Server error";
    public static final String REQUEST_NOT_FOUND = "Hearing request could not be found";

    @Override
    public Exception decode(String methodKey, Response response) {
        ErrorDetails errorDetails = getResponseBody(response, ErrorDetails.class)
            .orElseThrow(() -> new AuthenticationException(SERVER_ERROR));
        log.error(String.format("Response from FH failed with HTTP code %s, error code %s, error message '%s'",
                  response.status(),
                  errorDetails.getErrorCode(),
                  errorDetails.getErrorDescription()));

        if (log.isDebugEnabled()) {
            try (InputStream is = response.body().asInputStream()) {
                String payload = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.debug(String.format("Error payload from FH (HTTP %s): %s", response.status(), payload));
            } catch (IOException e) {
                log.error("Unable to read payload from FH", e);
            }
        }

        switch (response.status()) {
            case 400:
                return new BadFutureHearingRequestException(INVALID_REQUEST, errorDetails);
            case 401:
                return new AuthenticationException(INVALID_SECRET);
            case 404:
                return new ResourceNotFoundException(REQUEST_NOT_FOUND);
            default:
                return new AuthenticationException(SERVER_ERROR);
        }
    }

    private <T> Optional<T> getResponseBody(Response response, Class<T> klass) {
        try {
            String bodyJson = new BufferedReader(new InputStreamReader(response.body().asInputStream()))
                .lines().parallel().collect(Collectors.joining("\n"));
            return Optional.ofNullable(new ObjectMapper().readValue(bodyJson, klass));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

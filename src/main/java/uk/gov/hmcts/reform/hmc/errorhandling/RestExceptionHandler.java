package uk.gov.hmcts.reform.hmc.errorhandling;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        String[] errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getDefaultMessage())
            .toArray(String[]::new);
        log.debug("MethodArgumentNotValidException:{}", ex.getLocalizedMessage());
        return toResponseEntity(status, null, errors);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<Object> handleCustomException(AuthorizationException ex) {
        log.debug("Custom Exception exception: {}", ex.getMessage(), ex);
        return toResponseEntity(HttpStatus.BAD_REQUEST, ex.getLocalizedMessage());
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Object> handleFeignStatusException(FeignException ex) {
        String errorMessage = ex.responseBody()
            .map(res -> new String(res.array(), StandardCharsets.UTF_8))
            .orElse(ex.getMessage());
        log.error("Downstream service errors: {}", errorMessage, ex);
        return toResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
    }


    private ResponseEntity<Object> toResponseEntity(HttpStatus status, String message, String... errors) {
        ApiError apiError = new ApiError(status, message, errors == null ? null : List.of(errors));
        return new ResponseEntity<>(apiError, new HttpHeaders(), apiError.getStatus());

    }
}

package uk.gov.hmcts.reform.hmc.errorhandling;

import com.fasterxml.jackson.core.JsonProcessingException;

public class JsonProcessingRuntimeException extends RuntimeException {
    public JsonProcessingRuntimeException(JsonProcessingException cause) {
        super(cause);
    }
}

package uk.gov.hmcts.reform.hmc.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SyncMessage {

    @JsonProperty("listAssistHttpStatus")
    private Integer listAssistHttpStatus;

    @JsonProperty("listAssistErrorCode")
    private Integer listAssistErrorCode;

    @JsonProperty("listAssistErrorDescription")
    private String listAssistErrorDescription;
}

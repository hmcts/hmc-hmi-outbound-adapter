package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorDetails {

    @JsonProperty("errCode")
    private Integer errorCode;

    @JsonProperty("errorDesc")
    private String errorDescription;

    @JsonProperty("errLinkId")
    private String errorLinkId;

}

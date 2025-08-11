package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorDetails implements Serializable {

    @Serial
    private static final long serialVersionUID = 1440867514864615134L;

    @JsonProperty("errCode")
    private Integer errorCode;

    @JsonProperty("errorDesc")
    private String errorDescription;

    @JsonProperty("errLinkId")
    private String errorLinkId;

    @JsonProperty("error_codes")
    private List<Integer> authErrorCodes;

    @JsonProperty("error_description")
    private String authErrorDescription;

    @JsonProperty("statusCode")
    private Integer apiStatusCode;

    @JsonProperty("message")
    private String apiErrorMessage;
}

package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HearingManagementInterfaceResponse {

    @JsonProperty("response code")
    private Integer responseCode;

    private String description;

}

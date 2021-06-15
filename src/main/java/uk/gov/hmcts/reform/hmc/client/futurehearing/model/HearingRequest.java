package uk.gov.hmcts.reform.hmc.client.futurehearing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class HearingRequest {

    @JsonProperty("_case")
    private CaseDetails caseDetails;
}

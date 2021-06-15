package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.hmc.client.futurehearing.model.HearingRequest;

@Data
@NoArgsConstructor
public class HearingRequestPayload {

    @JsonProperty("hearingRequest")
    private HearingRequest hearingRequest;

}

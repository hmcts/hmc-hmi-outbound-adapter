package uk.gov.hmcts.reform.hmc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AuthenticationErrorDetails {

    @JsonProperty("error_codes")
    private List<Integer> error_codes ;

    @JsonProperty("error_description")
    private String error_description;

}

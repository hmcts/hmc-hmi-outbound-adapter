package uk.gov.hmcts.reform.hmc.client.futurehearing;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "future-hearing-api",
    url = "${fh.ad.host}"
)
public interface FutureHearingApiClient {

    String AUTH = "moreUrl";

    @GetMapping(value = AUTH, consumes = APPLICATION_FORM_URLENCODED_VALUE)
    AuthorizationResponse getAuthorizationToken(@RequestBody String request);

}

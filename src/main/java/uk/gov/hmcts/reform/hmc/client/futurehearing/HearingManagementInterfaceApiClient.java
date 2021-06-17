package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(
    name = "hearing-management-interface-api",
    url = "${fh.hmi.host}",
    configuration = {FutureHearingApiClientConfig.class, HearingManagementInterfaceApiClientConfig.class}
)
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public interface HearingManagementInterfaceApiClient {

    String HEARINGS_URL = "/hearings";
    String CASE_LISTING_URL = HEARINGS_URL + "/{cid}";

    @PostMapping(value = HEARINGS_URL, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    HearingManagementInterfaceResponse requestHearing(@RequestHeader(AUTHORIZATION) String token,
                                                      @RequestBody JsonNode data);

    @PutMapping(value = CASE_LISTING_URL, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    HearingManagementInterfaceResponse amendHearing(@PathVariable("cid") String caseListingRequestId,
                                                    @RequestHeader(AUTHORIZATION) String token,
                                                    @RequestBody JsonNode data);
}

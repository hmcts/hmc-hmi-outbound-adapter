package uk.gov.hmcts.reform.hmc.client.futurehearing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(
    name = "hearing-management-interface-api",
    url = "${fh.hmi.host}",
    configuration = {FutureHearingApiClientConfig.class}
)
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public interface HearingManagementInterfaceApiClient {

    String HEARINGS_URL = "/hearings";
    String CASE_URL = HEARINGS_URL + "/{cid}";

    @PostMapping(value = HEARINGS_URL, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    HearingManagementInterfaceResponse requestHearing(@RequestHeader(AUTHORIZATION) String authorization,
                        @RequestHeader("Source-System") String sourceSystem,
                        @RequestHeader("Destination-System") String destinationSystem,
                        @RequestHeader("Request-Created-At") String requestCreatedAt,
                        @RequestHeader("transactionIdHMCTS") UUID transactionIdHMCTS,
                        @RequestBody JsonNode data);

    @PutMapping(value = CASE_URL, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    HearingManagementInterfaceResponse amendHearing(@PathVariable("cid") String caseListingRequestId,
                                                    @RequestHeader(AUTHORIZATION) String authorization,
                                                    @RequestHeader("Source-System") String sourceSystem,
                                                    @RequestHeader("Destination-System") String destinationSystem,
                                                    @RequestHeader("Request-Created-At") String requestCreatedAt,
                                                    @RequestHeader("transactionIdHMCTS") UUID transactionIdHMCTS,
                                                    @RequestBody JsonNode data);
}

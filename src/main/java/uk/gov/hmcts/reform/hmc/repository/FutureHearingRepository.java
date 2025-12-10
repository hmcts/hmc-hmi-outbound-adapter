package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HealthCheckResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

public interface FutureHearingRepository {

    AuthenticationResponse retrieveAuthToken();

    HealthCheckResponse privateHealthCheck();

    HearingManagementInterfaceResponse createHearingRequest(JsonNode data, String caseListingRequestId);

    HearingManagementInterfaceResponse amendHearingRequest(JsonNode data, String caseListingRequestId);

    HearingManagementInterfaceResponse deleteHearingRequest(JsonNode data, String caseListingRequestId);


}

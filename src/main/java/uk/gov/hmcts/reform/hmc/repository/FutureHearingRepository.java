package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

public interface FutureHearingRepository {

    AuthenticationResponse retrieveAuthToken();

    HearingManagementInterfaceResponse createHearingRequest(JsonNode data);

    HearingManagementInterfaceResponse amendHearingRequest(JsonNode data, String caseListingRequestId);

    HearingManagementInterfaceResponse deleteHearingRequest(JsonNode data, String caseListingRequestId);


}

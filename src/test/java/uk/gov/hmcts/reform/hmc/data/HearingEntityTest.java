package uk.gov.hmcts.reform.hmc.data;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HearingEntityTest {

    @Nested
    class GetLatestCaseHearingRequest {

        @Test
        void shouldGetLatestHearingRequest() {
            HearingEntity hearing = new HearingEntity();
            CaseHearingRequestEntity caseHearingRequest1 = caseHearingRequest(1);
            CaseHearingRequestEntity caseHearingRequest2 = caseHearingRequest(2);
            CaseHearingRequestEntity caseHearingRequest3 = caseHearingRequest(3);
            hearing.setCaseHearingRequests(List.of(caseHearingRequest1, caseHearingRequest2, caseHearingRequest3));

            CaseHearingRequestEntity latestRequest = hearing.getLatestCaseHearingRequest();

            assertEquals(caseHearingRequest3, latestRequest);
        }

        @Test
        void shouldErrorWhenNoRequestsExistWithNullList() {
            HearingEntity hearing = new HearingEntity();
            hearing.setId(2000000001L);

            ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, hearing::getLatestCaseHearingRequest);
            assertEquals("Cannot find latest case hearing request for hearing 2000000001",
                         exception.getMessage());
        }
    }

    private CaseHearingRequestEntity caseHearingRequest(int version) {
        CaseHearingRequestEntity caseHearingRequest = new CaseHearingRequestEntity();
        caseHearingRequest.setVersionNumber(version);
        return caseHearingRequest;
    }


}

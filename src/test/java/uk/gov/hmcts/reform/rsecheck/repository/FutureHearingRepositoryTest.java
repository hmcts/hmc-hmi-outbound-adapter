package uk.gov.hmcts.reform.rsecheck.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingApiClient;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;


public class FutureHearingRepositoryTest {

    private AuthenticationResponse response;
    private String requestString;


    @InjectMocks
    private DefaultFutureHearingRepository repository;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private FutureHearingApiClient futureHearingApiClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        response = new AuthenticationResponse();
        repository = new DefaultFutureHearingRepository(futureHearingApiClient, applicationParams);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
    }

    @Test
    public void shouldSuccessfullyReturnAuthenticationObject() {
        given(futureHearingApiClient.authenticate(requestString)).willReturn(response);
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");

        AuthenticationResponse testResponse = repository.retrieveAuthToken();
        assertThat(testResponse.equals(response));
    }
}

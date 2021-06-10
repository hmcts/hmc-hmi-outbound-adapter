package uk.gov.hmcts.reform.hmc.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.client.futurehearing.ActiveDirectoryApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceApiClient;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

class FutureHearingRepositoryTest {

    private AuthenticationResponse response;
    private String requestString;
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private static final String SOURCE_SYSTEM = "SOURCE_SYSTEM";
    private static final String DESTINATION_SYSTEM = "DESTINATION_SYSTEM";
    private final Clock fixedClock = Clock.fixed(Instant.parse("2021-06-10T04:00:00.08Z"), ZoneOffset.UTC);

    @InjectMocks
    private DefaultFutureHearingRepository repository;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Mock
    private HearingManagementInterfaceApiClient hmiClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        response = new AuthenticationResponse();
        repository = new DefaultFutureHearingRepository(activeDirectoryApiClient, applicationParams, hmiClient,
                                                        fixedClock);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
        given(applicationParams.getSourceSystem()).willReturn(SOURCE_SYSTEM);
        given(applicationParams.getDestinationSystem()).willReturn(DESTINATION_SYSTEM);
    }

    @Test
    void shouldSuccessfullyReturnAuthenticationObject() {
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        AuthenticationResponse testResponse = repository.retrieveAuthToken();
        assertEquals(response, testResponse);
    }

    @Test
    void shouldSuccessfullyCreateHearingRequest() {
        HearingManagementInterfaceResponse expectedResponse = new HearingManagementInterfaceResponse();
        expectedResponse.setResponseCode(202);
        response.setAccessToken("test-token");
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        given(hmiClient.requestHearing("Bearer test-token", SOURCE_SYSTEM, DESTINATION_SYSTEM,
                                       fixedClock.instant().toString(), anyData)).willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.createHearingRequest(anyData);
        assertEquals(expectedResponse, actualResponse);
    }
}

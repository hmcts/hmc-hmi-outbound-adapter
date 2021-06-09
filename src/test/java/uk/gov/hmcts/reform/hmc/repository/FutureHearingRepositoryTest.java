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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


class FutureHearingRepositoryTest {

    private AuthenticationResponse response;
    private String requestString;
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();
    private String sourceSystem = "SOURCE_SYSTEM";
    private String destinationSystem = "DESTINATION_SYSTEM";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");


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
        repository = new DefaultFutureHearingRepository(activeDirectoryApiClient, applicationParams, hmiClient);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
        given(applicationParams.getSourceSystem()).willReturn(sourceSystem);
        given(applicationParams.getDestinationSystem()).willReturn(destinationSystem);
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
        JsonNode anyData = OBJECT_MAPPER.convertValue(response, JsonNode.class);
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        given(hmiClient.requestHearing("Bearer test-token", sourceSystem, destinationSystem,
                                       LocalDateTime.now().format(formatter), APPLICATION_JSON_VALUE,
                                       APPLICATION_JSON_VALUE, anyData)).willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.createHearingRequest(anyData);
        assertEquals(expectedResponse, actualResponse);
    }
}

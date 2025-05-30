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
import uk.gov.hmcts.reform.hmc.service.HearingStatusAuditServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FutureHearingRepositoryTest {

    private AuthenticationResponse response;
    private String requestString;
    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();

    private static final String CASE_LISTING_REQUEST_ID = "testCaseListingRequestId";

    @InjectMocks
    private DefaultFutureHearingRepository repository;

    @Mock
    private ApplicationParams applicationParams;

    @Mock
    private ActiveDirectoryApiClient activeDirectoryApiClient;

    @Mock
    private HearingManagementInterfaceApiClient hmiClient;

    @Mock
    private HearingRepository hearingRepository;

    @Mock
    private HearingStatusAuditServiceImpl hearingStatusAuditService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        response = new AuthenticationResponse();
        repository = new DefaultFutureHearingRepository(activeDirectoryApiClient, applicationParams, hmiClient,
                                                        hearingRepository,hearingStatusAuditService);
        requestString = "grant_type=GRANT_TYPE&client_id=CLIENT_ID&scope=SCOPE&client_secret=CLIENT_SECRET";
        given(applicationParams.getGrantType()).willReturn("GRANT_TYPE");
        given(applicationParams.getClientId()).willReturn("CLIENT_ID");
        given(applicationParams.getScope()).willReturn("SCOPE");
        given(applicationParams.getClientSecret()).willReturn("CLIENT_SECRET");
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
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(hmiClient.requestHearing("Bearer test-token", anyData))
            .willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.createHearingRequest(anyData,
                                                                                            CASE_LISTING_REQUEST_ID);
        assertEquals(expectedResponse, actualResponse);
        verify(hearingStatusAuditService,
               times(2))
            .saveAuditTriageDetailsWithUpdatedDate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldSuccessfullyAmendHearingRequest() {
        HearingManagementInterfaceResponse expectedResponse = new HearingManagementInterfaceResponse();
        expectedResponse.setResponseCode(202);
        response.setAccessToken("test-token");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(hmiClient.amendHearing(CASE_LISTING_REQUEST_ID, "Bearer test-token", anyData))
            .willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.amendHearingRequest(anyData,
                                                                                           CASE_LISTING_REQUEST_ID);
        assertEquals(expectedResponse, actualResponse);
        verify(hearingStatusAuditService,
               times(2))
            .saveAuditTriageDetailsWithUpdatedDate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldSuccessfullyDeleteHearingRequest() {
        HearingManagementInterfaceResponse expectedResponse = new HearingManagementInterfaceResponse();
        expectedResponse.setResponseCode(200);
        response.setAccessToken("test-token");
        given(activeDirectoryApiClient.authenticate(requestString)).willReturn(response);
        JsonNode anyData = OBJECT_MAPPER.convertValue("test data", JsonNode.class);
        given(hmiClient.deleteHearing(CASE_LISTING_REQUEST_ID, "Bearer test-token", anyData))
            .willReturn(expectedResponse);
        HearingManagementInterfaceResponse actualResponse = repository.deleteHearingRequest(anyData,
                                                                                           CASE_LISTING_REQUEST_ID);
        assertEquals(expectedResponse, actualResponse);
        verify(hearingStatusAuditService,
               times(2))
            .saveAuditTriageDetailsWithUpdatedDate(any(), any(), any(), any(), any(), any());
    }
}

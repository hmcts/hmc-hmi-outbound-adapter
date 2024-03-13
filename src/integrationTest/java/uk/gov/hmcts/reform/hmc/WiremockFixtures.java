package uk.gov.hmcts.reform.hmc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


public class WiremockFixtures {

    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String CLIENT_SECRET = "CLIENT_SECRET";
    private static final String SCOPE = "SCOPE";
    private static final String GRANT_TYPE = "GRANT_TYPE";
    private static final String GET_TOKEN_URL = "/FH_GET_TOKEN_URL";
    private static final String HMI_REQUEST_URL = "/hearings";
    private static final String SOURCE_SYSTEM = "SOURCE_SYSTEM";
    private static final String DESTINATION_SYSTEM = "DESTINATION_SYSTEM";

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String TEST_BODY = "{\n"
        + "    \"errCode\": \"1000\",\n"
        + "    \"errorDesc\": \"'300' is not a valid value for 'caseCourt.locationId'\",\n"
        + "    \"errorLinkId\": null,\n"
        + "    \"exception\": null\n"
        + "}";

    private WiremockFixtures() {
    }

    // Same issue as here https://github.com/tomakehurst/wiremock/issues/97
    public static class ConnectionClosedTransformer extends ResponseDefinitionTransformer {

        @Override
        public String getName() {
            return "keep-alive-disabler";
        }

        @Override
        public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition,
                                            FileSource files, Parameters parameters) {
            return ResponseDefinitionBuilder.like(responseDefinition)
                .withHeader(HttpHeaders.CONNECTION, "close")
                .build();
        }
    }

    public static void stubSuccessfullyReturnToken(String token) {
        AuthenticationResponse authenticationResponse = new AuthenticationResponse();
        authenticationResponse.setAccessToken(token);
        stubFor(post(urlEqualTo(GET_TOKEN_URL))
                    .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED_VALUE))
                    .withRequestBody(matching("grant_type=" + GRANT_TYPE + "&client_id=" + CLIENT_ID + "&scope="
                                                  + SCOPE + "&client_secret=" + CLIENT_SECRET))
                    .willReturn(aResponse()
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(authenticationResponse))
                                    .withStatus(200)
                    ));
    }

    public static void stubPostMethodThrowingAuthenticationError(int status, String url) {
        stubFor(post(urlEqualTo(url))
                    .willReturn(jsonResponse(TEST_BODY,status)));
    }

    public static void stubSuccessfullyRequestHearing(String token) {
        HearingManagementInterfaceResponse response =  new HearingManagementInterfaceResponse();
        response.setResponseCode(202);
        response.setDescription("The request was received successfully.");
        stubFor(post(urlEqualTo(HMI_REQUEST_URL))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Source-System", equalTo(SOURCE_SYSTEM))
                    .withHeader("Destination-System", equalTo(DESTINATION_SYSTEM))
                    .withHeader("Request-Created-At", matching("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]"
                                                                   + "{2}:[0-9]{2}Z"))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader("transactionIdHMCTS", matching("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]"
                                                                   + "{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
                    .willReturn(aResponse()
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(response))
                                    .withStatus(202)
                    ));
    }

    public static void stubPutMethodThrowingError(int status, String url) {
        stubFor(put(urlEqualTo(url))
                    .willReturn(jsonResponse(TEST_BODY, status)));
    }

    public static void stubSuccessfullyAmendHearing(String token, String caseListingRequestId) {
        HearingManagementInterfaceResponse response = new HearingManagementInterfaceResponse();
        response.setResponseCode(202);
        response.setDescription("The request was received successfully.");
        stubFor(put(urlEqualTo(HMI_REQUEST_URL + "/" + caseListingRequestId))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Source-System", equalTo(SOURCE_SYSTEM))
                    .withHeader("Destination-System", equalTo(DESTINATION_SYSTEM))
                    .withHeader("Request-Created-At", matching("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]"
                                                                   + "{2}:[0-9]{2}Z"))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader("transactionIdHMCTS", matching("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]"
                                                                   + "{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
                    .willReturn(aResponse()
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(response))
                                    .withStatus(202)
                    ));
    }

    public static void stubDeleteMethodThrowingError(int status, String url) {
        stubFor(delete(urlEqualTo(url))
                    .willReturn(jsonResponse(TEST_BODY, status)));
    }

    public static void stubSuccessfullyDeleteHearing(String token, String caseListingRequestId) {
        HearingManagementInterfaceResponse response = new HearingManagementInterfaceResponse();
        response.setResponseCode(200);
        response.setDescription("The request was received successfully.");
        stubFor(delete(urlEqualTo(HMI_REQUEST_URL + "/" + caseListingRequestId))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Source-System", equalTo(SOURCE_SYSTEM))
                    .withHeader("Destination-System", equalTo(DESTINATION_SYSTEM))
                    .withHeader("Request-Created-At", matching("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]"
                                                                   + "{2}:[0-9]{2}Z"))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader("transactionIdHMCTS", matching("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]"
                                                                   + "{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
                    .willReturn(aResponse()
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(response))
                                    .withStatus(200)
                    ));
    }

    private static String getJsonString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

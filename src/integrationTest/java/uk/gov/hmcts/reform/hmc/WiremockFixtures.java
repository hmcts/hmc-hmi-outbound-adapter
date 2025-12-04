package uk.gov.hmcts.reform.hmc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.springframework.boot.actuate.health.Status;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

import java.util.List;
import java.util.StringJoiner;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONNECTION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class WiremockFixtures {

    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String CLIENT_SECRET = "CLIENT_SECRET";
    private static final String SCOPE = "SCOPE";
    private static final String GRANT_TYPE = "GRANT_TYPE";
    private static final String GET_TOKEN_URL = "/FH_GET_TOKEN_URL";
    private static final String HMI_REQUEST_URL = "/hearings";
    private static final String HMI_PRIVATE_HEALTH_URL = "/health";
    private static final String SOURCE_SYSTEM = "SOURCE_SYSTEM";
    private static final String DESTINATION_SYSTEM = "DESTINATION_SYSTEM";

    private static final String HEADER_SOURCE_SYSTEM = "Source-System";
    private static final String HEADER_DESTINATION_SYSTEM = "Destination-System";
    private static final String HEADER_REQUEST_CREATED_AT = "Request-Created-At";
    private static final String HEADER_TRANSACTION_ID_HMCTS = "transactionIdHMCTS";

    private static final String REGEX_TIMESTAMP = "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z";
    private static final String REGEX_UUID =
        "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";

    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String TEST_BODY = """
        {
            "errCode": "1000",
            "errorDesc": "'300' is not a valid value for 'caseCourt.locationId'",
            "errorLinkId": null,
            "exception": null
        }""";

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
                .withHeader(CONNECTION, "close")
                .build();
        }
    }

    public static void stubSuccessfullyReturnToken(String token) {
        AuthenticationResponse authenticationResponse = new AuthenticationResponse();
        authenticationResponse.setAccessToken(token);
        stubFor(post(urlEqualTo(GET_TOKEN_URL))
                    .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FORM_URLENCODED_VALUE))
                    .withRequestBody(matching("grant_type=" + GRANT_TYPE + "&client_id=" + CLIENT_ID + "&scope="
                                                  + SCOPE + "&client_secret=" + CLIENT_SECRET))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(authenticationResponse))
                                    .withStatus(200)
                    ));
    }

    public static void stubFailToReturnToken(int status, String errorDescription, List<Integer> errorCodes) {
        StringJoiner joiner = new StringJoiner(",");
        errorCodes.forEach(value -> joiner.add(String.valueOf(value)));

        String adErrorResponse = """
            {
                "error_description": "%s",
                "error_codes" : [%s]
            }""".formatted(errorDescription, joiner.toString());

        stubFor(post(urlEqualTo(GET_TOKEN_URL))
                    .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FORM_URLENCODED_VALUE))
                    .withRequestBody(matching("grant_type=" + GRANT_TYPE + "&client_id=" + CLIENT_ID + "&scope="
                                                  + SCOPE + "&client_secret=" + CLIENT_SECRET))
                    .willReturn(jsonResponse(adErrorResponse, status))
        );
    }

    public static void stubFailToReturnTokenTimeout() {
        // Note: This needs to be used in conjunction with a smaller than the default
        // value for the feign client readTimeout property to trigger a timeout
        stubFor(post(urlEqualTo(GET_TOKEN_URL))
                    .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FORM_URLENCODED_VALUE))
                    .withRequestBody(matching("grant_type=" + GRANT_TYPE + "&client_id=" + CLIENT_ID + "&scope="
                                                  + SCOPE + "&client_secret=" + CLIENT_SECRET))
                    .willReturn(aResponse()
                                    .withFixedDelay(10000))
        );
    }

    public static void stubPostMethodThrowingAuthenticationError(int status, String url) {
        stubFor(post(urlEqualTo(url))
                    .willReturn(jsonResponse(TEST_BODY,status)));
    }

    public static void stubSuccessfullyRequestHearing(String token) {
        HearingManagementInterfaceResponse response = new HearingManagementInterfaceResponse();
        response.setResponseCode(202);
        response.setDescription("The request was received successfully.");
        stubFor(post(urlEqualTo(HMI_REQUEST_URL))
                    .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(HEADER_SOURCE_SYSTEM, equalTo(SOURCE_SYSTEM))
                    .withHeader(HEADER_DESTINATION_SYSTEM, equalTo(DESTINATION_SYSTEM))
                    .withHeader(HEADER_REQUEST_CREATED_AT, matching(REGEX_TIMESTAMP))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader(HEADER_TRANSACTION_ID_HMCTS, matching(REGEX_UUID))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
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
                    .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(HEADER_SOURCE_SYSTEM, equalTo(SOURCE_SYSTEM))
                    .withHeader(HEADER_DESTINATION_SYSTEM, equalTo(DESTINATION_SYSTEM))
                    .withHeader(HEADER_REQUEST_CREATED_AT, matching(REGEX_TIMESTAMP))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader(HEADER_TRANSACTION_ID_HMCTS, matching(REGEX_UUID))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
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
                    .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(HEADER_SOURCE_SYSTEM, equalTo(SOURCE_SYSTEM))
                    .withHeader(HEADER_DESTINATION_SYSTEM, equalTo(DESTINATION_SYSTEM))
                    .withHeader(HEADER_REQUEST_CREATED_AT, matching(REGEX_TIMESTAMP))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader(HEADER_TRANSACTION_ID_HMCTS, matching(REGEX_UUID))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(response))
                                    .withStatus(200)
                    ));
    }

    public static void stubHealthCheckThrowingError(int status, String message) {
        String hmiErrorResponse = """
            {
                "statusCode": %d,
                "message": "%s"
            }""".formatted(status, message);
        stubFor(get(urlEqualTo(HMI_PRIVATE_HEALTH_URL))
                    .willReturn(jsonResponse(hmiErrorResponse, status))
        );
    }

    public static void stubHealthCheck(String token, Status healthStatus) {
        stubFor(get(urlEqualTo(HMI_PRIVATE_HEALTH_URL))
                    .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
                    .withHeader(HEADER_SOURCE_SYSTEM, equalTo(SOURCE_SYSTEM))
                    .withHeader(HEADER_DESTINATION_SYSTEM, equalTo(DESTINATION_SYSTEM))
                    .withHeader(HEADER_REQUEST_CREATED_AT, matching(REGEX_TIMESTAMP))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .withHeader(HEADER_TRANSACTION_ID_HMCTS, matching(REGEX_UUID))
                    .willReturn(aResponse()
                                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(healthStatus))
                                    .withStatus(200)
                    )
        );
    }

    private static String getJsonString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

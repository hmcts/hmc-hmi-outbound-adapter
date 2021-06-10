package uk.gov.hmcts.reform.hmc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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

    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static String TEST_BODY = "This is a test message";


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
        stubFor(WireMock.post(urlEqualTo(GET_TOKEN_URL))
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
        stubFor(WireMock.post(urlEqualTo(url))
                    .willReturn(okJson(TEST_BODY).withStatus(status)));
    }

    public static void stubSuccessfullyRequestHearing(String token) {
        HearingManagementInterfaceResponse response =  new HearingManagementInterfaceResponse();
        response.setResponseCode(202);
        response.setDescription("The request was received successfully.");
        stubFor(WireMock.post(urlEqualTo(HMI_REQUEST_URL))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Source-System", equalTo(SOURCE_SYSTEM))
                    .withHeader("Destination-System", equalTo(DESTINATION_SYSTEM))
                    .withHeader("Request-Created-At", equalTo(LocalDateTime.now().format(formatter)))
                    .withHeader(AUTHORIZATION, equalTo("Bearer " + token))
                    .willReturn(aResponse()
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withBody(getJsonString(response))
                                    .withStatus(202)
                    ));
    }

    @SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes", "squid:S112"})
    // Required as wiremock's Json.getObjectMapper().registerModule(..); not working
    // see https://github.com/tomakehurst/wiremock/issues/1127
    private static String getJsonString(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

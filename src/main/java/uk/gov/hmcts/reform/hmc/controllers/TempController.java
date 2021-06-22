package uk.gov.hmcts.reform.hmc.controllers;
/*
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.client.futurehearing.HearingManagementInterfaceResponse;
import uk.gov.hmcts.reform.hmc.repository.FutureHearingRepository;

//To be removed after testing, comment out to enable test welcomeRootEndpoint() to pass
@RestController
public class TempController {

    private static final ObjectMapper OBJECT_MAPPER = new Jackson2ObjectMapperBuilder()
        .modules(new Jdk8Module())
        .build();

    private final FutureHearingRepository futureHearingRepository;

    public TempController(FutureHearingRepository futureHearingRepository) {
        this.futureHearingRepository = futureHearingRepository;
    }

    @GetMapping("/something")
    public AuthenticationResponse assignAccessWithinOrganisation() {
        return futureHearingRepository.retrieveAuthToken();
    }

    @PostMapping("/something")
    public HearingManagementInterfaceResponse createHearingRequest(@RequestBody Object data) {
        JsonNode anyData = OBJECT_MAPPER.convertValue(data, JsonNode.class);
        return futureHearingRepository.createHearingRequest(anyData);
    }

    @PutMapping("/something")
    public HearingManagementInterfaceResponse amendHearingRequest(@RequestBody Object data,
    @RequestHeader("RequestId") String caseListinRequestId) {
        JsonNode anyData = OBJECT_MAPPER.convertValue(data, JsonNode.class);
        return futureHearingRepository.amendHearingRequest(anyData, caseListinRequestId);
    }

    @DeleteMapping("/something")
    public HearingManagementInterfaceResponse deleteHearingRequest(@RequestBody Object data,
    @RequestHeader("RequestId") String caseListinRequestId) {
        JsonNode anyData = OBJECT_MAPPER.convertValue(data, JsonNode.class);
        return futureHearingRepository.deleteHearingRequest(anyData, caseListinRequestId);
    }
}*/

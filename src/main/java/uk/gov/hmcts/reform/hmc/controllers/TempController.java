package uk.gov.hmcts.reform.hmc.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.repository.FutureHearingRepository;

//remove after testing
@RestController
public class TempController {

    private final FutureHearingRepository futureHearingRepository;

    public TempController(FutureHearingRepository futureHearingRepository) {
        this.futureHearingRepository = futureHearingRepository;
    }

    @GetMapping("/something")
    public AuthenticationResponse assignAccessWithinOrganisation() {
        return futureHearingRepository.retrieveAuthToken();
    }
}

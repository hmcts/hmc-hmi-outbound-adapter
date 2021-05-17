package uk.gov.hmcts.reform.hmc.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

@RestController
public class TempController {

    private final DefaultFutureHearingRepository defaultFhRepo;

    public TempController(DefaultFutureHearingRepository defaultFhRepo) {
        this.defaultFhRepo = defaultFhRepo;
    }

    @GetMapping("/something")
    public AuthenticationResponse assignAccessWithinOrganisation() {
        return defaultFhRepo.retrieveAuthToken();
    }
}

package uk.gov.hmcts.reform.hmc.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;

@RestController
public class TempController {

    private final DefaultFutureHearingRepository defaultFHRepo;

    public TempController(DefaultFutureHearingRepository defaultFHRepo) {
        this.defaultFHRepo = defaultFHRepo;
    }

    @GetMapping("/something")
    public String assignAccessWithinOrganisation() {
        return defaultFHRepo.retrieveAuthToken();
    }
}

package uk.gov.hmcts.reform.hmc.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.BaseTest;
import uk.gov.hmcts.reform.hmc.client.futurehearing.AuthenticationResponse;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.hmc.WiremockFixtures.stubThrowAuthorizationError;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_REQUEST;
import static uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingErrorDecoder.INVALID_SECRET;

public class FutureHearingRepositoryIT {


    @Nested
    @DisplayName("GET")
    class GetAuthenticationToken extends BaseTest {

        @Autowired
        private ApplicationParams applicationParams;

        @Autowired
        private DefaultFutureHearingRepository defaultFutureHearingRepository;

        @Test
        public void shouldSuccessfullyReturnAuthenticationObject() {
            AuthenticationResponse response = defaultFutureHearingRepository.retrieveAuthToken();
            assertEquals(response.getAccessToken(), "example-token");

        }

        @Test
        public void shouldThrow400AuthenticationException() {
            stubThrowAuthorizationError(400);
            assertThatThrownBy(() -> defaultFutureHearingRepository.retrieveAuthToken())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_REQUEST);

        }

        @Test
        public void shouldThrow401AuthenticationException() {
            stubThrowAuthorizationError(401);
            assertThatThrownBy(() -> defaultFutureHearingRepository.retrieveAuthToken())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(INVALID_SECRET);

        }

        @Test
        public void shouldThrow500AuthenticationException() {
            stubThrowAuthorizationError(500);
            assertThatThrownBy(() -> defaultFutureHearingRepository.retrieveAuthToken())
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Server Error");
        }

    }

}


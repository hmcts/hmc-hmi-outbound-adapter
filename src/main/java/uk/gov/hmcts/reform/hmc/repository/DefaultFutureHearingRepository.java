package uk.gov.hmcts.reform.hmc.repository;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.hmc.client.futurehearing.FutureHearingApiClient;
import uk.gov.hmcts.reform.hmc.errorhandling.AuthorizationException;

@Repository("defaultFutureHearingRepository")
public class DefaultFutureHearingRepository implements FutureHearingRepository {

    private final FutureHearingApiClient FHApi;

    private static final Logger LOG = LoggerFactory.getLogger(DefaultFutureHearingRepository.class);

    public DefaultFutureHearingRepository(FutureHearingApiClient FHApi) {
        this.FHApi = FHApi;
    }


    public String retrieveAuthToken() {
       try {
           return FHApi.getAuthorizationToken(
                "").getAccessToken();
        } catch (FeignException e) {
           LOG.error(e.getMessage());
           if(e.status() == HttpStatus.BAD_REQUEST.value()){
               throw new AuthorizationException("Missing one or more required parameters");
           }
           if(e.status() == HttpStatus.UNAUTHORIZED.value())
           {
               throw new AuthorizationException("Invaild secrets");
           }
           if(e.status() == HttpStatus.INTERNAL_SERVER_ERROR.value())
           {
               throw e;
           }
        }
       return null;
    }

}

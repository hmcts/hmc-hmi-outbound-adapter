package uk.gov.hmcts.reform.hmc.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;

@Transactional(propagation = Propagation.REQUIRED)
@Repository
public interface HearingRepository extends CrudRepository<HearingEntity, Long> {

}

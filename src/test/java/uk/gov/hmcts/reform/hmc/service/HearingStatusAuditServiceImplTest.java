package uk.gov.hmcts.reform.hmc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.hmc.constants.Constants.FAILURE_STATUS;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_AUTH;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.SUCCESS_STATUS;

class HearingStatusAuditServiceImplTest {

    @InjectMocks
    private HearingStatusAuditServiceImpl hearingStatusAuditService;

    @Mock
    HearingStatusAuditRepository hearingStatusAuditRepository;

    @Mock
    HearingRepository hearingRepository;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        hearingStatusAuditService =
            new HearingStatusAuditServiceImpl(hearingStatusAuditRepository);
    }

    @Nested
    @DisplayName("HearingStatusAudit")
    class HearingStatusAuditDetails {
        @Test
        void shouldSaveAuditTriageDetailsWhenFailure() throws JsonProcessingException {
            JsonNode errorDetails = new ObjectMapper().readTree("{\"deadLetterReason\":"
                                                                    + " \"MaxDeliveryCountExceeded \"}");
            HearingStatusAuditEntity auditEntity = TestingUtil.hearingStatusAuditEntity(HMC_TO_HMI_AUTH,
                                                                                        FAILURE_STATUS, HMC, HMI,
                                                                                        errorDetails).get();
            given(hearingStatusAuditRepository.save(auditEntity)).willReturn(auditEntity);
            hearingStatusAuditService. saveAuditTriageDetails(TestingUtil.hearingEntity().get(),
                                                              HMC_TO_HMI_AUTH, FAILURE_STATUS, HMC, HMI,
                                                              errorDetails);
            assertEquals("2000000000", auditEntity.getHearingId());
            assertEquals(FAILURE_STATUS, auditEntity.getHttpStatus());
            assertEquals(HMC, auditEntity.getSource());
            assertEquals(errorDetails, auditEntity.getErrorDescription());
            assertEquals(HMC_TO_HMI_AUTH, auditEntity.getHearingEvent());
            verify(hearingStatusAuditRepository, times(1)).save(any());
        }

        @Test
        void shouldSaveAuditTriageDetailsWhenSuccess() {
            HearingStatusAuditEntity auditEntity = TestingUtil.hearingStatusAuditEntity(HMC_TO_HMI_AUTH,
                                                                                        SUCCESS_STATUS, HMC, HMI,
                                                                                        null).get();
            given(hearingStatusAuditRepository.save(auditEntity)).willReturn(auditEntity);
            given(hearingStatusAuditRepository.findById(1L)).willReturn(
                TestingUtil.hearingStatusAuditEntity(HMC_TO_HMI_AUTH, FAILURE_STATUS, HMC, HMI,null));

            hearingStatusAuditService. saveAuditTriageDetails(TestingUtil.hearingEntity().get(),
                                                              HMC_TO_HMI_AUTH, SUCCESS_STATUS, HMC, HMI,
                                                              null);
            assertEquals("2000000000", auditEntity.getHearingId());
            assertEquals(SUCCESS_STATUS, auditEntity.getHttpStatus());
            assertEquals(HMC, auditEntity.getSource());
            assertNull(auditEntity.getErrorDescription());
            assertEquals(HMC_TO_HMI_AUTH, auditEntity.getHearingEvent());
            verify(hearingStatusAuditRepository, times(1)).save(any());
        }
    }

}

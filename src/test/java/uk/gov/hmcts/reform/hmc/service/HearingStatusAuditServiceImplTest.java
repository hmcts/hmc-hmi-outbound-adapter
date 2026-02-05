package uk.gov.hmcts.reform.hmc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingStatusAuditEntity;
import uk.gov.hmcts.reform.hmc.model.HearingStatusAuditContext;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.hmc.constants.Constants.CREATE_HEARING_REQUEST;
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

    @Captor
    private ArgumentCaptor<HearingStatusAuditEntity> hearingStatusAuditEntityCaptor;

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
            HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
            hearingEntity.setCreatedDateTime(LocalDateTime.now());
            JsonNode errorDetails = new ObjectMapper().readTree("{\"deadLetterReason\":"
                                                                    + " \"MaxDeliveryCountExceeded \"}");
            HearingStatusAuditContext context =
                HearingStatusAuditContext.builder()
                    .hearingEntity(hearingEntity)
                    .hearingEvent(HMC_TO_HMI_AUTH)
                    .httpStatus(FAILURE_STATUS)
                    .source(HMC)
                    .target(HMI)
                    .errorDetails(errorDetails)
                    .build();
            hearingStatusAuditService.saveAuditTriageDetails(context);
            HearingStatusAuditEntity auditEntity = getHearingStatusAuditEntity();
            assertEquals("2000000000", auditEntity.getHearingId());
            assertEquals(FAILURE_STATUS, auditEntity.getHttpStatus());
            assertEquals(HMC, auditEntity.getSource());
            assertEquals(errorDetails, auditEntity.getErrorDescription());
            assertEquals(HMC_TO_HMI_AUTH, auditEntity.getHearingEvent());
            assertEquals("Test", auditEntity.getHmctsServiceId());
            assertEquals("1", auditEntity.getRequestVersion());
            assertEquals(hearingEntity.getCreatedDateTime(), auditEntity.getStatusUpdateDateTime());
            assertNotNull(auditEntity.getErrorDescription());
        }

        @Test
        void shouldSaveAuditTriageDetailsWhenSuccess() {
            HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
            hearingEntity.setCreatedDateTime(LocalDateTime.now());
            HearingStatusAuditContext context =
                HearingStatusAuditContext.builder()
                    .hearingEntity(hearingEntity)
                    .hearingEvent(HMC_TO_HMI_AUTH)
                    .httpStatus(SUCCESS_STATUS)
                    .source(HMC)
                    .target(HMI)
                    .build();
            hearingStatusAuditService.saveAuditTriageDetails(context);
            HearingStatusAuditEntity auditEntity = getHearingStatusAuditEntity();
            assertEquals("2000000000", auditEntity.getHearingId());
            assertEquals(SUCCESS_STATUS, auditEntity.getHttpStatus());
            assertEquals(HMC, auditEntity.getSource());
            assertNull(auditEntity.getErrorDescription());
            assertEquals(HMC_TO_HMI_AUTH, auditEntity.getHearingEvent());
            assertEquals("Test", auditEntity.getHmctsServiceId());
            assertEquals("1", auditEntity.getRequestVersion());
            assertEquals(hearingEntity.getCreatedDateTime(), auditEntity.getStatusUpdateDateTime());
            assertNull(auditEntity.getErrorDescription());
        }

        @Test
        void shouldSaveAuditTriageDetailsWithUpdatedDateWhenSuccess_UpdateTimeIsNotNull() {
            HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
            hearingEntity.setUpdatedDateTime(LocalDateTime.now());
            HearingStatusAuditContext context =
                HearingStatusAuditContext.builder()
                    .hearingEntity(hearingEntity)
                    .hearingEvent(CREATE_HEARING_REQUEST)
                    .httpStatus(SUCCESS_STATUS)
                    .source(HMC)
                    .target(HMI)
                    .build();
            hearingStatusAuditService.saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(context);
            HearingStatusAuditEntity auditEntity = getHearingStatusAuditEntity();
            assertEquals(SUCCESS_STATUS, auditEntity.getHttpStatus());
            assertEquals(CREATE_HEARING_REQUEST, auditEntity.getHearingEvent());
            assertEquals(context.getHearingEntity().getUpdatedDateTime(), auditEntity.getStatusUpdateDateTime());
        }

        @Test
        void shouldSaveAuditTriageDetailsWithUpdatedDateWhenSuccess_UpdateTimeIsNull() {
            HearingEntity hearingEntity = TestingUtil.hearingEntity().get();
            HearingStatusAuditContext context =
                HearingStatusAuditContext.builder()
                    .hearingEntity(hearingEntity)
                    .hearingEvent(CREATE_HEARING_REQUEST)
                    .httpStatus(SUCCESS_STATUS)
                    .source(HMC)
                    .target(HMI)
                    .build();
            LocalDateTime startDateTime = LocalDateTime.now();
            LocalDateTime startDateTimePlusFiveMinutes = startDateTime.plusMinutes(5);
            hearingStatusAuditService.saveAuditTriageDetailsWithUpdatedDateOrCurrentDate(context);
            HearingStatusAuditEntity auditEntity = getHearingStatusAuditEntity();
            assertTrue(auditEntity.getStatusUpdateDateTime().isAfter(startDateTime)
                           && auditEntity.getStatusUpdateDateTime().isBefore(startDateTimePlusFiveMinutes));
            assertEquals(SUCCESS_STATUS, auditEntity.getHttpStatus());
            assertEquals(CREATE_HEARING_REQUEST, auditEntity.getHearingEvent());
        }
    }

    private HearingStatusAuditEntity getHearingStatusAuditEntity() {
        verify(hearingStatusAuditRepository).save(hearingStatusAuditEntityCaptor.capture());
        HearingStatusAuditEntity savedEntity = hearingStatusAuditEntityCaptor.getValue();
        return savedEntity;
    }
}

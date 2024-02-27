package uk.gov.hmcts.reform.hmc.service;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
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
import org.powermock.api.mockito.PowerMockito;
import uk.gov.hmcts.reform.hmc.helper.HearingStatusAuditMapper;
import uk.gov.hmcts.reform.hmc.repository.HearingRepository;
import uk.gov.hmcts.reform.hmc.repository.HearingStatusAuditRepository;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_AUTH;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_FAILURE_STATUS;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI_SUCCESS_STATUS;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMI;

class HearingStatusAuditServiceImplTest {

    @InjectMocks
    private HearingStatusAuditServiceImpl hearingStatusAuditService;

    @Mock
    HearingStatusAuditRepository hearingStatusAuditRepository;

    @Mock
    HearingStatusAuditMapper hearingStatusAuditMapper;

    @Mock
    HearingRepository hearingRepository;

    @Mock
    ServiceBusReceivedMessageContext messageContext = mock(ServiceBusReceivedMessageContext.class);

    @Mock
    ServiceBusReceivedMessage receivedMessage;

    private static final String HEARING_ID = "2000000000";

    private static final String ERROR_MESSAGE = "This is a test error message";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        hearingStatusAuditService =
            new HearingStatusAuditServiceImpl(hearingStatusAuditRepository,
                                              hearingStatusAuditMapper,
                                              hearingRepository);
    }

    @Nested
    @DisplayName("HearingStatusAudit")
    class HearingStatusAuditDetails {
        @Test
        void shouldSaveAuditTriageDetailsWhenFailure() throws JsonProcessingException {
            JsonNode errorDetails = new ObjectMapper().readTree("{\"deadLetterReason\":"
                                                                    + " \"MaxDeliveryCountExceeded \"}");
            given(hearingStatusAuditMapper.modelToEntity(TestingUtil.hearingStatusAudit())).willReturn(
                TestingUtil.hearingStatusAuditEntity());
            given(hearingStatusAuditRepository.save(TestingUtil.hearingStatusAuditEntity())).willReturn(
                TestingUtil.hearingStatusAuditEntity());
            hearingStatusAuditService. saveAuditTriageDetails(TestingUtil.hearingEntity(),
                                                              HMC_TO_HMI_AUTH, HMC_TO_HMI_FAILURE_STATUS, HMC, HMI,
                                                              errorDetails);
            verify(hearingStatusAuditRepository, times(1)).save(any());
        }

        @Test
        void shouldSaveAuditTriageDetailsWhenSuccess() {
            given(hearingStatusAuditMapper.modelToEntity(TestingUtil.hearingStatusAudit())).willReturn(
                TestingUtil.hearingStatusAuditEntity());
            given(hearingStatusAuditRepository.save(TestingUtil.hearingStatusAuditEntity())).willReturn(
                TestingUtil.hearingStatusAuditEntity());
            hearingStatusAuditService. saveAuditTriageDetails(TestingUtil.hearingEntity(),
                                                              HMC_TO_HMI_AUTH, HMC_TO_HMI_SUCCESS_STATUS, HMC, HMI,
                                                              null);
            verify(hearingStatusAuditRepository, times(1)).save(any());
        }

        @Test
        void getErrorDetails()  {
            Map<String, Object> applicationProperties = new HashMap<>();
            applicationProperties.put("hearing_id", HEARING_ID);
            given(messageContext.getMessage()).willReturn(receivedMessage);
            given(messageContext.getMessage().getMessageId()).willReturn(HEARING_ID);
            PowerMockito.when(messageContext.getMessage().getApplicationProperties()).thenReturn(applicationProperties);
            given(hearingRepository.findById(Long.valueOf(HEARING_ID))).willReturn(TestingUtil.hearingEntity());
            hearingStatusAuditService. getErrorDetails(messageContext, ERROR_MESSAGE);
            verify(hearingStatusAuditRepository, times(1)).save(any());
        }
    }

}

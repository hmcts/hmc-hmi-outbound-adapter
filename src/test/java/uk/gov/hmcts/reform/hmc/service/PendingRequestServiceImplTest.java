package uk.gov.hmcts.reform.hmc.service;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HEARING_ID;

@Nested
@DisplayName("PendingRequestServiceImpl")
class PendingRequestServiceImplTest {

    @InjectMocks
    private PendingRequestServiceImpl pendingRequestService;

    @Mock
    private PendingRequestRepository pendingRequestRepository;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldAddToPendingRequestsSuccessfully() {
        ServiceBusMessage message = mock(ServiceBusMessage.class);
        when(message.getBody()).thenReturn(BinaryData.fromString("Test message body"));
        Map<String, Object> applicationProperties = new HashMap<String, Object>();
        applicationProperties.put(HEARING_ID, 1L);
        when(message.getApplicationProperties()).thenReturn(applicationProperties);

        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        when(pendingRequestRepository.save(any(PendingRequestEntity.class))).thenReturn(pendingRequest);

        pendingRequestService.addToPendingRequests(message);

        verify(pendingRequestRepository, times(1)).save(any(PendingRequestEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenHearingIdNotFoundInMessage() {
        ServiceBusMessage message = mock(ServiceBusMessage.class);
        when(message.getBody()).thenReturn(BinaryData.fromString("Test message body"));

        Map<String, Object> applicationProperties = new HashMap<String, Object>();
        when(message.getApplicationProperties()).thenReturn(applicationProperties);
        assertThrows(IllegalArgumentException.class, () -> {
            pendingRequestService.addToPendingRequests(message);
        });
    }

    @Test
    void shouldLockPendingRequestsByHearingId() {
        Long hearingId = 1L;
        List<PendingRequestEntity> pendingRequests = List.of(new PendingRequestEntity());
        when(pendingRequestRepository.findAndLockByHearingId(hearingId)).thenReturn(pendingRequests);

        List<PendingRequestEntity> result = pendingRequestService.findAndLockByHearingId(hearingId);

        assertEquals(pendingRequests, result);
        verify(pendingRequestRepository, times(1)).findAndLockByHearingId(hearingId);
    }

    @Test
    void shouldReturnOldestPendingRequestForProcessing() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        when(pendingRequestRepository.findOldestPendingRequestForProcessing()).thenReturn(pendingRequest);

        PendingRequestEntity result = pendingRequestService.findOldestPendingRequestForProcessing();

        assertEquals(pendingRequest, result);
        verify(pendingRequestRepository, times(1)).findOldestPendingRequestForProcessing();
    }

    @Test
    void shouldMarkRequestAsProcessing() {
        long id = 1L;
        pendingRequestService.markRequestAsProcessing(id);

        verify(pendingRequestRepository, times(1)).markRequestAsProcessing(id);
    }

    @Test
    void shouldMarkRequestAsPending() {
        long id = 1L;
        int retryCount = 1;
        pendingRequestService.markRequestAsPending(id, retryCount);

        verify(pendingRequestRepository, times(1)).markRequestAsPending(id, retryCount + 1);
    }

    @Test
    void shouldMarkRequestAsCompleted() {
        long id = 1L;
        pendingRequestService.markRequestAsCompleted(id);

        verify(pendingRequestRepository, times(1)).markRequestAsCompleted(id);
    }

    @Test
    void shouldMarkRequestAsException() {
        long id = 1L;
        pendingRequestService.markRequestAsException(id);

        verify(pendingRequestRepository, times(1)).markRequestAsException(id);
    }

    @Test
    void shouldDeleteCompletedRecords() {
        pendingRequestService.deleteCompletedRecords();

        verify(pendingRequestRepository, times(1)).deleteCompletedRecords();
    }
}

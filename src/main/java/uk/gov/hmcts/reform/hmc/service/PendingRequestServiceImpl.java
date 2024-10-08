package uk.gov.hmcts.reform.hmc.service;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static uk.gov.hmcts.reform.hmc.constants.Constants.HEARING_ID;

@Slf4j
@Service
public class PendingRequestServiceImpl implements PendingRequestService {

    private final PendingRequestRepository pendingRequestRepository;

    public PendingRequestServiceImpl(PendingRequestRepository pendingRequestRepository) {
        this.pendingRequestRepository = pendingRequestRepository;
    }

    public boolean submittedDateTimePeriodElapsed(PendingRequestEntity pendingRequest) {
        log.debug("submittedDateTimePeriodElapsed() hearingId<{}>", pendingRequest.getHearingId());
        LocalDateTime currentDateTime = LocalDateTime.now();
        LocalDateTime submittedDateTime = pendingRequest.getSubmittedDateTime().toLocalDateTime();
        long hoursElapsed = ChronoUnit.HOURS.between(submittedDateTime, currentDateTime);
        log.debug("Hours elapsed = {}; submittedDateTime: {}; currentDateTime: {}",
                  hoursElapsed, submittedDateTime, currentDateTime);
        if (hoursElapsed >= 24) {
            log.debug("Marking hearing request {} as Exception (hours elapsed exceeds limit!)",
                      pendingRequest.getHearingId());
            markRequestAsException(pendingRequest.getId());
            log.error("Submitted time of request with ID {} is 24 hours later than before.",
                      pendingRequest.getHearingId());
            identifyRequestsForEscalation();
            return true;
        }
        return false;
    }

    public boolean lastTriedDateTimePeriodNotElapsed(PendingRequestEntity pendingRequest) {
        log.debug("lastTriedDateTimePeriodNotElapsed() hearingId<{}>", pendingRequest.getHearingId());
        LocalDateTime currentDateTime = LocalDateTime.now();
        LocalDateTime lastTriedDateTime = pendingRequest.getLastTriedDateTime().toLocalDateTime();
        long minutesElapsed = ChronoUnit.MINUTES.between(lastTriedDateTime, currentDateTime);
        log.debug("Minutes elapsed = {}; submittedDateTime: {}; currentDateTime: {}",
                  minutesElapsed, lastTriedDateTime, currentDateTime);
        return (minutesElapsed < 15);
    }

    public void addToPendingRequests(Object message) {
        log.debug("addToPendingRequests(message)");
        PendingRequestEntity pendingRequest = createPendingRequestEntity(message);
        try {
            log.debug("pendingRequest: {}", pendingRequest);
            pendingRequestRepository.save(pendingRequest);
        } catch (Exception e) {
            log.error("Failed to add message to pending requests", e);
        }
    }

    public PendingRequestEntity findById(Long id) {
        log.debug("findById({})", id);
        return pendingRequestRepository.findById(id).orElse(null);
    }

    public List<PendingRequestEntity> findAndLockByHearingId(Long hearingId) {
        log.debug("findAndLockByHearingId({})", hearingId);
        return pendingRequestRepository.findAndLockByHearingId(hearingId);
    }

    public PendingRequestEntity findOldestPendingRequestForProcessing() {
        PendingRequestEntity pendingRequest = pendingRequestRepository.findOldestPendingRequestForProcessing();
        if (null != pendingRequest) {
            log.debug("findOldestPendingRequestForProcessing(): id<{}> hearingId<{}> ",
                      pendingRequest.getId(), pendingRequest.getHearingId());
        } else {
            log.debug("findOldestPendingRequestForProcessing(): null");
        }
        return pendingRequest;
    }

    public void markRequestAsProcessing(Long id) {
        log.debug("markRequestAsProcessing({})", id);
        pendingRequestRepository.markRequestAsProcessing(id);
    }

    public void markRequestAsPending(Long id, Integer retryCount) {
        log.debug("markRequestAsPending({}, {})", id, retryCount);
        pendingRequestRepository.markRequestAsPending(id, retryCount + 1);
    }

    public void markRequestAsCompleted(Long id) {
        log.debug("markRequestAsCompleted({})", id);
        pendingRequestRepository.markRequestAsCompleted(id);
    }

    public void markRequestAsException(Long id) {
        log.debug("markRequestAsException({})", id);
        pendingRequestRepository.markRequestAsException(id);
    }

    public void identifyRequestsForEscalation() {
        pendingRequestRepository.identifyRequestsForEscalation();
    }

    public void deleteCompletedRecords() {
        log.debug("delete completed PendingRequests");
        pendingRequestRepository.deleteCompletedRecords();
    }

    private PendingRequestEntity createPendingRequestEntity(Object message) {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(0L);
        String messageBody = getMessageBody(message);
        pendingRequest.setMessage(messageBody);

        Object hearingIdValue = getApplicationProperties(message).get(HEARING_ID);
        if (hearingIdValue != null) {
            pendingRequest.setHearingId(Long.valueOf(hearingIdValue.toString()));
        } else {
            throw new IllegalArgumentException("HEARING_ID not found in message application properties - messageBody<"
                                                   + messageBody + ">");
        }

        pendingRequest.setStatus("PENDING");
        pendingRequest.setIncidentFlag(false);
        pendingRequest.setVersionNumber(1);

        Timestamp currentTimestamp = Timestamp.valueOf(LocalDateTime.now());
        pendingRequest.setLastTriedDateTime(currentTimestamp);
        pendingRequest.setSubmittedDateTime(currentTimestamp);
        pendingRequest.setRetryCount(0);

        return pendingRequest;
    }

    private String getMessageBody(Object message) {
        if (message instanceof ServiceBusReceivedMessage serviceBusReceivedMessage) {
            return serviceBusReceivedMessage.getBody().toString();
        } else if (message instanceof ServiceBusMessage serviceBusReceivedMessage) {
            return serviceBusReceivedMessage.getBody().toString();
        } else {
            throw new IllegalArgumentException("Unsupported message type");
        }
    }

    private Map<String, Object> getApplicationProperties(Object message) {
        if (message instanceof ServiceBusReceivedMessage serviceBusReceivedMessage) {
            return serviceBusReceivedMessage.getApplicationProperties();
        } else if (message instanceof ServiceBusMessage serviceBusReceivedMessage) {
            return serviceBusReceivedMessage.getApplicationProperties();
        } else {
            throw new IllegalArgumentException("Unsupported message type");
        }
    }

}

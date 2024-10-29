package uk.gov.hmcts.reform.hmc.entity;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.hmc.config.MessageType;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.sql.Timestamp;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PendingRequestEntityTest {

    final long id = 1L;
    final long hearingId = 12345L;
    final int versionNumber = 1;
    final Timestamp submittedDateTime = Timestamp.valueOf("2023-10-01 10:00:00");
    final int retryCount = 3;
    final Timestamp lastTriedDateTime = Timestamp.valueOf("2023-10-02 09:00:00");
    final String status = "PENDING";
    final boolean incidentFlag = true;
    final String message = "Test Message";
    final String messageType = MessageType.REQUEST_HEARING.name();
    final String deploymentId = "depIdXX";

    @Test
    void testPendingRequestEntity() {

        PendingRequestEntity pendingRequest = generatePendingRequest();

        assertThat(id).isEqualTo(pendingRequest.getId());
        assertThat(hearingId).isEqualTo(pendingRequest.getHearingId());
        assertThat(versionNumber).isEqualTo(pendingRequest.getVersionNumber());
        assertThat(submittedDateTime).isEqualTo(pendingRequest.getSubmittedDateTime());
        assertThat(retryCount).isEqualTo(pendingRequest.getRetryCount());
        assertThat(lastTriedDateTime).isEqualTo(pendingRequest.getLastTriedDateTime());
        assertThat(status).isEqualTo(pendingRequest.getStatus());
        assertThat(incidentFlag).isEqualTo(pendingRequest.getIncidentFlag());
        assertThat(message).isEqualTo(pendingRequest.getMessage());
        assertThat(messageType).isEqualTo(pendingRequest.getMessageType());
        assertThat(deploymentId).isEqualTo(pendingRequest.getDeploymentId());

        final String expectedString =
            "id:<" + id + ">,hearingId:<" + hearingId + ">,versionNumber:<" + versionNumber
                + ">,messageType:<" + messageType + ">,submittedDateTime:<"
                + submittedDateTime + ">,retryCount:<" + retryCount + ">,"
            + "lastTriedDateTime:<" + lastTriedDateTime + ">,status:<" + status + ">,incidentFlag:<" + incidentFlag
                + ">,message:<" + message + ">,deploymentId:<" + deploymentId + ">";
        assertThat(expectedString).isEqualTo(pendingRequest.toString());
    }

    @Test
    void shouldReturnTrueForEqualEntities() {
        PendingRequestEntity pendingRequest1 = generatePendingRequest();
        PendingRequestEntity pendingRequest2 = generatePendingRequest();

        assertThat(pendingRequest1).isEqualTo(pendingRequest2);
    }

    @Test
    void shouldReturnFalseForNonEqualEntities() {
        PendingRequestEntity pendingRequest1 = generatePendingRequest();

        PendingRequestEntity pendingRequest2 = generatePendingRequest();
        pendingRequest2.setId(2L);

        assertThat(pendingRequest1).isNotEqualTo(pendingRequest2);
    }

    @Test
    void shouldReturnCorrectHashCode() {
        PendingRequestEntity pendingRequest = generatePendingRequest();

        int expectedHashCode = Objects.hash(id, hearingId, versionNumber, messageType, submittedDateTime, retryCount,
                                             lastTriedDateTime, status, incidentFlag, message, deploymentId);
        assertThat(expectedHashCode).isEqualTo(pendingRequest.hashCode());
    }

    @Test
    void shouldReturnCorrectStringRepresentation() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(12345L);
        pendingRequest.setVersionNumber(1);
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf("2023-10-01 10:00:00"));
        pendingRequest.setRetryCount(3);
        pendingRequest.setLastTriedDateTime(Timestamp.valueOf("2023-10-02 09:00:00"));
        pendingRequest.setStatus("PENDING");
        pendingRequest.setIncidentFlag(true);
        pendingRequest.setMessage("Test Message");
        pendingRequest.setMessageType("REQUEST_HEARING");
        pendingRequest.setDeploymentId("depIdXX");

        String expectedString = "id:<1>,hearingId:<12345>,versionNumber:<1>,messageType:<REQUEST_HEARING>,"
            + "submittedDateTime:<2023-10-01 10:00:00.0>,retryCount:<3>,lastTriedDateTime:<2023-10-02 09:00:00.0>,"
            + "status:<PENDING>,incidentFlag:<true>,message:<Test Message>,deploymentId:<depIdXX>";
        assertThat(expectedString).isEqualTo(pendingRequest.toString());
    }

    private PendingRequestEntity generatePendingRequest() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(id);
        pendingRequest.setHearingId(hearingId);
        pendingRequest.setVersionNumber(versionNumber);
        pendingRequest.setSubmittedDateTime(submittedDateTime);
        pendingRequest.setRetryCount(retryCount);
        pendingRequest.setLastTriedDateTime(lastTriedDateTime);
        pendingRequest.setStatus(status);
        pendingRequest.setIncidentFlag(incidentFlag);
        pendingRequest.setMessage(message);
        pendingRequest.setMessageType(messageType);
        pendingRequest.setDeploymentId(deploymentId);
        return pendingRequest;
    }
}

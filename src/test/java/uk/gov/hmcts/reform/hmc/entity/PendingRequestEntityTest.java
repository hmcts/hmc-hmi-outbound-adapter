package uk.gov.hmcts.reform.hmc.entity;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.hmc.config.MessageType;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PendingRequestEntityTest {

    @Test
    void testPendingRequestEntity() {
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

        assertEquals(id, pendingRequest.getId());
        assertEquals(hearingId, pendingRequest.getHearingId());
        assertEquals(versionNumber, pendingRequest.getVersionNumber());
        assertEquals(submittedDateTime, pendingRequest.getSubmittedDateTime());
        assertEquals(retryCount, pendingRequest.getRetryCount());
        assertEquals(lastTriedDateTime, pendingRequest.getLastTriedDateTime());
        assertEquals(status, pendingRequest.getStatus());
        assertEquals(incidentFlag, pendingRequest.getIncidentFlag());
        assertEquals(message, pendingRequest.getMessage());
        assertEquals(messageType, pendingRequest.getMessageType());
        assertEquals(deploymentId, pendingRequest.getDeploymentId());

        final String expectedString =
            "id:<" + id + ">,hearingId:<" + hearingId + ">,versionNumber:<" + versionNumber
                + ">,messageType:<" + messageType + ">,submittedDateTime:<"
                + submittedDateTime + ">,retryCount:<" + retryCount + ">,"
            + "lastTriedDateTime:<" + lastTriedDateTime + ">,status:<" + status + ">,incidentFlag:<" + incidentFlag
                + ">,message:<" + message + ">,deploymentId:<" + deploymentId + ">";
        assertEquals(expectedString, pendingRequest.toString());
    }

    @Test
    void shouldReturnCorrectApplicationProperties() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setHearingId(12345L);
        pendingRequest.setMessageType("REQUEST_HEARING");

        Map<String, Object> properties = pendingRequest.getApplicationProperties();

        assertEquals(12345L, properties.get("hearing_id"));
        assertEquals("REQUEST_HEARING", properties.get("message_type"));
    }

    @Test
    void shouldReturnTrueForEqualEntities() {
        PendingRequestEntity pendingRequest1 = new PendingRequestEntity();
        pendingRequest1.setId(1L);
        pendingRequest1.setHearingId(12345L);

        PendingRequestEntity pendingRequest2 = new PendingRequestEntity();
        pendingRequest2.setId(1L);
        pendingRequest2.setHearingId(12345L);

        assertEquals(pendingRequest1, pendingRequest2);
    }

    @Test
    void shouldReturnFalseForNonEqualEntities() {
        PendingRequestEntity pendingRequest1 = new PendingRequestEntity();
        pendingRequest1.setId(1L);
        pendingRequest1.setHearingId(12345L);

        PendingRequestEntity pendingRequest2 = new PendingRequestEntity();
        pendingRequest2.setId(2L);
        pendingRequest2.setHearingId(12345L);

        assertNotEquals(pendingRequest1, pendingRequest2);
    }

    @Test
    void shouldReturnCorrectHashCode() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setId(1L);
        pendingRequest.setHearingId(12345L);

        int expectedHashCode = Objects.hash(1L, 12345L, null, null, null, null, null, null, null, null, null);
        assertEquals(expectedHashCode, pendingRequest.hashCode());
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

        String expectedString = "id:<1>,hearingId:<12345>,versionNumber:<1>,messageType:<REQUEST_HEARING>,submittedDateTime:<2023-10-01 10:00:00.0>,retryCount:<3>,lastTriedDateTime:<2023-10-02 09:00:00.0>,status:<PENDING>,incidentFlag:<true>,message:<Test Message>,deploymentId:<depIdXX>";
        assertEquals(expectedString, pendingRequest.toString());
    }
}

package uk.gov.hmcts.reform.hmc.data;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingRequestEntityTest {

    @Test
    void testPendingRequestEntity() {
        PendingRequestEntity pendingRequest = new PendingRequestEntity();
        pendingRequest.setHearingId(12345L);
        pendingRequest.setVersionNumber(1);
        pendingRequest.setSubmittedDateTime(Timestamp.valueOf("2023-10-01 10:00:00"));
        pendingRequest.setRetryCount(3);
        pendingRequest.setLastTriedDateTime(Timestamp.valueOf("2023-10-02 10:00:00"));
        pendingRequest.setStatus("Pending");
        pendingRequest.setIncidentFlag(true);
        pendingRequest.setMessage("Test message");

        assertEquals(12345L, pendingRequest.getHearingId());
        assertEquals(1, pendingRequest.getVersionNumber());
        assertEquals(Timestamp.valueOf("2023-10-01 10:00:00"), pendingRequest.getSubmittedDateTime());
        assertEquals(3, pendingRequest.getRetryCount());
        assertEquals(Timestamp.valueOf("2023-10-02 10:00:00"), pendingRequest.getLastTriedDateTime());
        assertEquals("Pending", pendingRequest.getStatus());
        assertEquals(true, pendingRequest.getIncidentFlag());
        assertEquals("Test message", pendingRequest.getMessage());

        final String expectedString =
            "hearingId:<12345>,versionNumber:<1>,submittedDateTime:<2023-10-01 10:00:00.0>,retryCount:<3>,"
            + "lastTriedDateTime:<2023-10-02 10:00:00.0>,status:<Pending>,incidentFlag:<true>,message:<Test message>";
        assertEquals(expectedString, pendingRequest.toString());
    }
}

package uk.gov.hmcts.reform.hmc.data;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(id, pendingRequest.getId());
        assertEquals(hearingId, pendingRequest.getHearingId());
        assertEquals(versionNumber, pendingRequest.getVersionNumber());
        assertEquals(submittedDateTime, pendingRequest.getSubmittedDateTime());
        assertEquals(retryCount, pendingRequest.getRetryCount());
        assertEquals(lastTriedDateTime, pendingRequest.getLastTriedDateTime());
        assertEquals(status, pendingRequest.getStatus());
        assertEquals(incidentFlag, pendingRequest.getIncidentFlag());
        assertEquals(message, pendingRequest.getMessage());

        final String expectedString =
            "id:<" + id + ">,hearingId:<" + hearingId + ">,versionNumber:<" + versionNumber + ">,submittedDateTime:<"
                + submittedDateTime + ">,retryCount:<" + retryCount + ">,"
            + "lastTriedDateTime:<" + lastTriedDateTime + ">,status:<" + status + ">,incidentFlag:<" + incidentFlag
                + ">,message:<" + message + ">";
        assertEquals(expectedString, pendingRequest.toString());
    }
}

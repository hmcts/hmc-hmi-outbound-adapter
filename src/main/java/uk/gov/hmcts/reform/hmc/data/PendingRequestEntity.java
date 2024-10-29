package uk.gov.hmcts.reform.hmc.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

@Table(name = "pending_requests")
@Entity
@Setter
@Getter
@NoArgsConstructor
public class PendingRequestEntity implements Serializable {

    private static final long serialVersionUID = -5832580267716907071L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,
        generator = "pending_requests_id_seq")
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "hearing_id")
    private Long hearingId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "message_type")
    private String messageType;

    @Column(name = "submitted_date_time", nullable = false)
    private Timestamp submittedDateTime;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_tried_date_time", nullable = false)
    private Timestamp lastTriedDateTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "incident_flag")
    private Boolean incidentFlag;

    @Column(name = "message")
    private String message;

    @Column(name = "deployment_id")
    private String deploymentId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PendingRequestEntity that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(hearingId, that.hearingId) && Objects.equals(
            versionNumber,
            that.versionNumber
        ) && Objects.equals(messageType, that.messageType) && Objects.equals(
            submittedDateTime,
            that.submittedDateTime
        ) && Objects.equals(retryCount, that.retryCount) && Objects.equals(
            lastTriedDateTime,
            that.lastTriedDateTime
        ) && Objects.equals(status, that.status) && Objects.equals(incidentFlag, that.incidentFlag) && Objects.equals(
            message,
            that.message
        ) && Objects.equals(deploymentId, that.deploymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            hearingId,
            versionNumber,
            messageType,
            submittedDateTime,
            retryCount,
            lastTriedDateTime,
            status,
            incidentFlag,
            message,
            deploymentId
        );
    }

    public String toString() {
        String sb = "id:<" + id + ">," +
            "hearingId:<" + hearingId + ">," +
            "versionNumber:<" + versionNumber + ">," +
            "messageType:<" + messageType + ">," +
            "submittedDateTime:<" + submittedDateTime + ">," +
            "retryCount:<" + retryCount + ">," +
            "lastTriedDateTime:<" + lastTriedDateTime + ">," +
            "status:<" + status + ">," +
            "incidentFlag:<" + incidentFlag + ">," +
            "message:<" + message + ">," +
            "deploymentId:<" + deploymentId + ">";
        return sb;
    }
}

package uk.gov.hmcts.reform.hmc.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.sql.Timestamp;

@Table(name = "pending_requests")
@Entity
@Data
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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id:<").append(id).append(">,")
            .append("hearingId:<").append(hearingId).append(">,")
            .append("versionNumber:<").append(versionNumber).append(">,")
            .append("submittedDateTime:<").append(submittedDateTime).append(">,")
            .append("retryCount:<").append(retryCount).append(">,")
            .append("lastTriedDateTime:<").append(lastTriedDateTime).append(">,")
            .append("status:<").append(status).append(">,")
            .append("incidentFlag:<").append(incidentFlag).append(">,")
            .append("message:<").append(message).append(">");
        return sb.toString();
    }
}

package uk.gov.hmcts.reform.hmc.data;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Table(name = "hearing_status_audit")
@EqualsAndHashCode()
@Entity
@Data
public class HearingStatusAuditEntity implements Serializable  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "hmcts_service_id", nullable = false)
    private String hmctsServiceId;

    @Column(name = "hearing_id", nullable = false)
    private String hearingId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "status_update_date_time", nullable = false)
    private LocalDateTime statusUpdateDateTime;

    @Column(name = "hearing_event", nullable = false)
    private String hearingEvent;

    @Column(name = "http_status")
    private String httpStatus;

    @Column(name = "source")
    private String source;

    @Column(name = "target")
    private String target;

    @Column(name = "error_description", columnDefinition = "jsonb")
    @Convert(converter = JsonDataConverter.class)
    @SuppressWarnings("java:S2789")
    private JsonNode errorDescription;

    @Column(name = "request_version", nullable = false)
    private String requestVersion;

    @Column(name = "response_date_time")
    private LocalDateTime responseDateTime;

    @Column(name = "other_info", columnDefinition = "jsonb")
    @Convert(converter = JsonDataConverter.class)
    @SuppressWarnings("java:S2789")
    private JsonNode otherInfo = null;

}

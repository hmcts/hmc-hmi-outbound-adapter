package uk.gov.hmcts.reform.hmc.data;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Table(name = "hearing_response")
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class HearingResponseEntity extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 2394768382139064486L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,
        generator = "hearing_response_id_seq")
    @Column(name = "hearing_response_id")
    private Long hearingResponseId;

    @Column(name = "received_date_time", nullable = false)
    private LocalDateTime requestTimeStamp;

    @Column(name = "listing_status")
    private String listingStatus;

    @Column(name = "listing_case_status", nullable = false)
    private String listingCaseStatus;

    @Column(name = "list_assist_transaction_id", nullable = false)
    private String listAssistTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hearing_id")
    private HearingEntity hearing;

    @OneToMany(mappedBy = "hearingResponse", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonSerialize
    @ToString.Exclude
    @JsonBackReference
    private List<HearingDayDetailsEntity> hearingDayDetails;

    @Column(name = "request_version", nullable = false)
    private Integer requestVersion;

    @Column(name = "parties_notified_datetime")
    private LocalDateTime partiesNotifiedDateTime;

    @Column(name = "service_data", columnDefinition = "jsonb")
    @Convert(converter = JsonDataConverter.class)
    @SuppressWarnings("java:S2789")
    private JsonNode serviceData;

    @Column(name = "cancellation_reason_type")
    private String cancellationReasonType;

    @Column(name = "translator_required")
    private Boolean translatorRequired;

    @Column(name = "listing_transaction_id")
    private String listingTransactionId;

}

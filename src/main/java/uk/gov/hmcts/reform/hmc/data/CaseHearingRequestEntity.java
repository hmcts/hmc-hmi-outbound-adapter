package uk.gov.hmcts.reform.hmc.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table(name = "case_hearing_request")
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@NoArgsConstructor
public class CaseHearingRequestEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 3931265105942318969L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
        generator = "case_hearing_id_seq_generator")
    @SequenceGenerator(name = "case_hearing_id_seq_generator",
        sequenceName = "case_hearing_id_seq", allocationSize = 1)
    @Column(name = "case_hearing_id")
    private Long caseHearingID;

    @Column(name = "auto_list_flag", nullable = false)
    private Boolean autoListFlag;

    @Column(name = "listing_auto_change_reason_code", length = 70)
    private String listingAutoChangeReasonCode;

    @Column(name = "hearing_type", nullable = false)
    private String hearingType;

    @Column(name = "required_duration_in_minutes", nullable = false)
    private Integer requiredDurationInMinutes;

    @Column(name = "hearing_priority_type", nullable = false)
    private String hearingPriorityType;

    @Column(name = "number_of_physical_attendees")
    private Integer numberOfPhysicalAttendees;

    @Column(name = "hearing_in_welsh_flag")
    private Boolean hearingInWelshFlag;

    @Column(name = "private_hearing_required_flag")
    private Boolean privateHearingRequiredFlag;

    @Column(name = "lead_judge_contract_type")
    private String leadJudgeContractType;

    @Column(name = "first_date_time_of_hearing_must_be")
    private LocalDateTime firstDateTimeOfHearingMustBe;

    @Column(name = "hmcts_service_code", nullable = false)
    private String hmctsServiceCode;

    @Column(name = "case_reference", nullable = false)
    private String caseReference;

    @Column(name = "external_case_reference")
    private String externalCaseReference;

    @Column(name = "case_url_context_path", nullable = false)
    private String caseUrlContextPath;

    @Column(name = "hmcts_internal_case_name", nullable = false)
    private String hmctsInternalCaseName;

    @Column(name = "public_case_name", nullable = false)
    private String publicCaseName;

    @Column(name = "additional_security_required_flag")
    private Boolean additionalSecurityRequiredFlag;

    @Column(name = "owning_location_id", nullable = false)
    private String owningLocationId;

    @Column(name = "case_restricted_flag", nullable = false)
    private Boolean caseRestrictedFlag;

    @Column(name = "case_sla_start_date", nullable = false)
    private LocalDate caseSlaStartDate;

    @Column(name = "hearing_request_version", nullable = false)
    private Integer versionNumber;

    @Column(name = "interpreter_booking_required_flag")
    private Boolean interpreterBookingRequiredFlag;

    @Column(name = "listing_comments")
    private String listingComments;

    @Column(name = "requester")
    private String requester;

    @Column(name = "hearing_window_start_date_range")
    private LocalDate hearingWindowStartDateRange;

    @Column(name = "hearing_window_end_date_range")
    private LocalDate hearingWindowEndDateRange;

    @Column(name = "hearing_request_received_date_time", nullable = false)
    private LocalDateTime hearingRequestReceivedDateTime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hearing_id")
    private HearingEntity hearing;

}

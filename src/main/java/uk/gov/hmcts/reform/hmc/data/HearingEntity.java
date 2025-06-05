package uk.gov.hmcts.reform.hmc.data;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Table(name = "hearing")
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@SecondaryTable(name = "CASE_HEARING_REQUEST",
    pkJoinColumns = {
        @PrimaryKeyJoinColumn(name = "CASE_HEARING_ID")})
public class HearingEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 5837513924648640249L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,
        generator = "hearing_id_seq")
    @Column(name = "hearing_id")
    private Long id;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_code")
    private Integer errorCode;

    @Column(name = "error_description")
    private String errorDescription;

    @Column(name = "updated_date_time")
    private LocalDateTime updatedDateTime;

    @OneToMany(mappedBy = "hearing", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<CaseHearingRequestEntity> caseHearingRequests = new ArrayList<>();

    @OneToMany(mappedBy = "hearing", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<HearingResponseEntity> hearingResponses;

    @Column(name = "linked_order")
    private Long linkedOrder;

    @Column(name = "is_linked_flag")
    private Boolean isLinkedFlag;

    @Column(name = "deployment_id")
    private String deploymentId;

    public CaseHearingRequestEntity getLatestCaseHearingRequest() {
        return getCaseHearingRequests().stream()
            .max(Comparator.comparingInt(CaseHearingRequestEntity::getVersionNumber))
            .orElseThrow(() -> new ResourceNotFoundException("Cannot find latest case "
                                                                 + "hearing request for hearing " + id));
    }

    public String getLatestCaseReferenceNumber() {
        return getLatestCaseHearingRequest().getCaseReference();
    }

    /**
     * Gets the *latest* hearing response - note that this will not necessarily be associated with the latest request.
     */
    public Optional<HearingResponseEntity> getLatestHearingResponse() {
        return hasHearingResponses() ? getHearingResponses().stream()
            .collect(groupingBy(HearingResponseEntity::getRequestVersion, TreeMap::new, toList()))
            .lastEntry()
            .getValue()
            .stream()
            .max(Comparator.comparing(HearingResponseEntity::getRequestTimeStamp))
            : Optional.empty();
    }

    public boolean hasHearingResponses() {
        return getHearingResponses() != null && !getHearingResponses().isEmpty();
    }

    public CaseHearingRequestEntity getCaseHearingRequest(int version) {
        return getCaseHearingRequests().stream()
            .filter(caseHearingRequestEntity -> version == caseHearingRequestEntity.getVersionNumber())
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Cannot find request version " + version
                                                                 + " for hearing " + id));
    }
}

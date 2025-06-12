package uk.gov.hmcts.reform.hmc.data;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Table(name = "hearing")
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
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

}

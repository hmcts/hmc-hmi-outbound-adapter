package uk.gov.hmcts.reform.hmc.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import uk.gov.hmcts.reform.hmc.errorhandling.ResourceNotFoundException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.SecondaryTable;
import javax.persistence.Table;

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

    @OneToMany(mappedBy = "hearing", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @LazyCollection(LazyCollectionOption.FALSE)
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
}

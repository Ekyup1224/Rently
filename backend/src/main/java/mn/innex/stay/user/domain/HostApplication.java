package mn.innex.stay.user.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

/**
 * A guest's request to become a house owner or hotel manager. Submitting one
 * grants nothing; the Step 4 admin approval queue decides, and approval is what
 * creates the role grant (and, for hotels, the organization).
 */
@Entity
@Table(name = "host_applications")
public class HostApplication {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 32)
    private Role requestedRole;

    /** Required for HOTEL_MANAGER applications, unused for HOUSE_OWNER. */
    @Column(name = "org_name", length = 200)
    private String organizationName;

    @Column(name = "org_registration_no", length = 64)
    private String organizationRegistrationNo;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HostApplicationStatus status = HostApplicationStatus.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected HostApplication() {
    }

    public HostApplication(User user, Role requestedRole, String organizationName,
                           String organizationRegistrationNo, String note) {
        this.user = user;
        this.requestedRole = requestedRole;
        this.organizationName = organizationName;
        this.organizationRegistrationNo = organizationRegistrationNo;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void withdraw() {
        this.status = HostApplicationStatus.WITHDRAWN;
        this.decidedAt = Instant.now();
    }

    public void decide(HostApplicationStatus decision, UUID decidedBy, String decisionNote) {
        this.status = decision;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
        this.decisionNote = decisionNote;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Role getRequestedRole() {
        return requestedRole;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationRegistrationNo() {
        return organizationRegistrationNo;
    }

    public String getNote() {
        return note;
    }

    public HostApplicationStatus getStatus() {
        return status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

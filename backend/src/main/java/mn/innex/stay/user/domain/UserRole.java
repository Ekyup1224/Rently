package mn.innex.stay.user.domain;

import java.time.Instant;
import java.util.Objects;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

/**
 * One role grant. Organization-scoped roles (hotel manager and staff) carry the
 * organization they apply to; platform-wide roles carry none.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    /** Null when the grant was made by the system rather than an admin. */
    @Column(name = "granted_by")
    private UUID grantedBy;

    protected UserRole() {
    }

    UserRole(User user, Role role, Organization organization, UUID grantedBy) {
        this.user = user;
        this.role = role;
        this.organization = organization;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void onCreate() {
        grantedAt = Instant.now();
    }

    boolean matches(Role otherRole, Organization otherOrganization) {
        if (this.role != otherRole) {
            return false;
        }
        UUID mine = organization == null ? null : organization.getId();
        UUID theirs = otherOrganization == null ? null : otherOrganization.getId();
        return Objects.equals(mine, theirs);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }
}

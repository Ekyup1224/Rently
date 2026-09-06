package mn.innex.stay.user.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * Platform account. Phone is the primary identifier: every user has one, and it
 * is what the OTP login flow keys on. Email and password are both optional,
 * because an account created purely through OTP never has either.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** E.164, normalized by {@code PhoneNumbers.normalize} before it ever gets here. */
    @Column(nullable = false, length = 20, unique = true)
    private String phone;

    /** Lower-cased. Unique when present, null for OTP-only accounts. */
    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus = KycStatus.NONE;

    @Column(nullable = false, length = 8)
    private String locale = "mn";

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // Batched so mapping a page of users costs a handful of queries rather than one per row.
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> roles = new LinkedHashSet<>();

    protected User() {
    }

    public static User createWithPhone(String e164Phone, String locale) {
        User user = new User();
        user.phone = e164Phone;
        user.locale = locale == null || locale.isBlank() ? "mn" : locale;
        user.status = UserStatus.PENDING_VERIFICATION;
        return user;
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

    /** Marks the phone verified and, on first verification, activates the account. */
    public void markPhoneVerified() {
        if (phoneVerifiedAt == null) {
            phoneVerifiedAt = Instant.now();
        }
        if (status == UserStatus.PENDING_VERIFICATION) {
            status = UserStatus.ACTIVE;
        }
    }

    public void markEmailVerified() {
        if (email != null && emailVerifiedAt == null) {
            emailVerifiedAt = Instant.now();
        }
    }

    public void recordLogin() {
        lastLoginAt = Instant.now();
    }

    /**
     * Adds a role grant if the user does not already hold it.
     *
     * @return true when a new grant was added
     */
    public boolean grantRole(Role role, Organization organization, UUID grantedBy) {
        if (role.isOrganizationScoped() == (organization == null)) {
            throw new IllegalArgumentException(
                    "Role " + role + " organization scoping mismatch");
        }
        boolean alreadyHeld = roles.stream().anyMatch(existing -> existing.matches(role, organization));
        if (alreadyHeld) {
            return false;
        }
        roles.add(new UserRole(this, role, organization, grantedBy));
        return true;
    }

    /** @return true when a matching grant existed and was removed */
    public boolean revokeRole(Role role, Organization organization) {
        return roles.removeIf(existing -> existing.matches(role, organization));
    }

    public boolean hasRole(Role role) {
        return roles.stream().anyMatch(grant -> grant.getRole() == role);
    }

    public Set<Role> roleNames() {
        return roles.stream().map(UserRole::getRole)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<UUID> organizationIds() {
        return roles.stream()
                .map(UserRole::getOrganization)
                .filter(Objects::nonNull)
                .map(Organization::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneVerifiedAt = null;
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public void setEmail(String normalizedEmail) {
        this.email = normalizedEmail;
        this.emailVerifiedAt = null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof User user && id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}

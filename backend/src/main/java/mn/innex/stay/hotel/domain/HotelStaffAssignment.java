package mn.innex.stay.hotel.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import mn.innex.stay.user.domain.User;
import org.hibernate.annotations.UuidGenerator;

/**
 * Which hotel a staff account works at.
 *
 * <p>The {@code HOTEL_STAFF} role grant (organization-scoped, from Step 1) is what
 * authorizes them; these rows narrow it to specific hotels, so front-desk staff at
 * one property cannot read another's reservations even within the same business.
 * A {@code HOTEL_MANAGER} needs no assignment: they see every hotel in their
 * organization.
 */
@Entity
@Table(name = "hotel_staff")
public class HotelStaffAssignment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    protected HotelStaffAssignment() {
    }

    public HotelStaffAssignment(Hotel hotel, User user, UUID assignedBy) {
        this.hotel = hotel;
        this.user = user;
        this.assignedBy = assignedBy;
    }

    @PrePersist
    void onCreate() {
        assignedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public User getUser() {
        return user;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }
}

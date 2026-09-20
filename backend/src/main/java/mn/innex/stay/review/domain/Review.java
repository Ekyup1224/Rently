package mn.innex.stay.review.domain;

import java.time.Instant;
import java.util.Map;
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
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.user.domain.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One person's account of one stay.
 *
 * <p>Written blind and published late: {@link #visible} stays false until the
 * other side writes theirs or the window closes. A review written while the
 * other is already on screen is a reply rather than an assessment, and the
 * ratings that come out of that are worth nothing to the next guest.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ReviewSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_kind", length = 16, updatable = false)
    private SupplyKind supplyKind;

    @Column(name = "supply_id", updatable = false)
    private UUID supplyId;

    @Column(nullable = false)
    private int rating;

    /** cleanliness / accuracy / location / value, each 1–5, all optional. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sub_ratings", nullable = false)
    private Map<String, Integer> subRatings = Map.of();

    @Column(columnDefinition = "text")
    private String comment;

    @Column(columnDefinition = "text")
    private String response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    private boolean visible;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(Booking booking, User author, ReviewSubject subject, SupplyKind supplyKind,
                  UUID supplyId, int rating, Map<String, Integer> subRatings, String comment) {
        this.booking = booking;
        this.author = author;
        this.subject = subject;
        this.supplyKind = supplyKind;
        this.supplyId = supplyId;
        this.rating = rating;
        this.subRatings = subRatings == null ? Map.of() : subRatings;
        this.comment = comment;
    }

    /** Makes it readable. Only ever called once, when the blind period ends. */
    public void publish() {
        if (!visible) {
            this.visible = true;
            this.publishedAt = Instant.now();
        }
    }

    /** The reviewed side's reply. Allowed once, and only after publication. */
    public void respond(String text) {
        this.response = text;
        this.respondedAt = Instant.now();
    }

    public void hide(String reason) {
        this.status = ReviewStatus.HIDDEN;
        this.hiddenReason = reason;
    }

    public void restore() {
        this.status = ReviewStatus.PUBLISHED;
        this.hiddenReason = null;
    }

    /**
     * Whether anyone but the author can read this.
     *
     * <p>Two separate facts have to hold, and they mean different things:
     * {@code visible} says the blind period is over, {@code status} says no
     * moderator has taken it down. Callers almost always want the conjunction —
     * asking {@code isVisible()} alone reports a hidden review as readable.
     */
    public boolean isReadable() {
        return visible && status == ReviewStatus.PUBLISHED;
    }

    /** Whether this review should count towards a listing's average. */
    public boolean countsTowardsRating() {
        return isReadable() && subject == ReviewSubject.SUPPLY;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public User getAuthor() {
        return author;
    }

    public ReviewSubject getSubject() {
        return subject;
    }

    public SupplyKind getSupplyKind() {
        return supplyKind;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public int getRating() {
        return rating;
    }

    public Map<String, Integer> getSubRatings() {
        return subRatings;
    }

    public String getComment() {
        return comment;
    }

    public String getResponse() {
        return response;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public boolean isVisible() {
        return visible;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package mn.innex.stay.messaging.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import mn.innex.stay.booking.domain.Booking;
import org.hibernate.annotations.UuidGenerator;

/** One thread, about one stay. */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Drives ordering in the inbox, so the liveliest thread is on top. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ConversationParticipant> participants = new LinkedHashSet<>();

    protected Conversation() {
    }

    public Conversation(Booking booking) {
        this.booking = booking;
    }

    public void addParticipant(ConversationParticipant participant) {
        participants.add(participant);
    }

    public void touch(Instant when) {
        this.lastMessageAt = when;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Set<ConversationParticipant> getParticipants() {
        return participants;
    }
}

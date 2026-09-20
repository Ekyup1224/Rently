package mn.innex.stay.messaging.domain;

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
 * One message.
 *
 * <p>A flagged message is still delivered. Silently swallowing someone's words
 * on a keyword match would break real conversations — a host explaining a bank
 * transfer for a legitimate deposit reads much like a scammer — so the flag
 * raises a review instead of censoring.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    @Column(name = "flagged_reason", length = 64)
    private String flaggedReason;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected Message() {
    }

    public Message(Conversation conversation, User sender, String body, String flaggedReason) {
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.flaggedReason = flaggedReason;
    }

    @PrePersist
    void onCreate() {
        this.sentAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public String getFlaggedReason() {
        return flaggedReason;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

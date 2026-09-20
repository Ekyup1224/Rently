package mn.innex.stay.messaging.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import mn.innex.stay.user.domain.User;

/**
 * Someone's membership of a thread, and how much of it they have read.
 *
 * <p>{@code lastReadAt} rather than a per-message read flag: an unread count is
 * the only thing anyone needs, and a timestamp answers it with one comparison.
 */
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

    @EmbeddedId
    private Key key = new Key();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    protected ConversationParticipant() {
    }

    public ConversationParticipant(Conversation conversation, User user) {
        this.conversation = conversation;
        this.user = user;
        this.key = new Key(conversation.getId(), user.getId());
    }

    public void markRead(Instant when) {
        this.lastReadAt = when;
    }

    public User getUser() {
        return user;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "conversation_id")
        private UUID conversationId;

        @Column(name = "user_id")
        private UUID userId;

        protected Key() {
        }

        Key(UUID conversationId, UUID userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(conversationId, key.conversationId)
                    && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}

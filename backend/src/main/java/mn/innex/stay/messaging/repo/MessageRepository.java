package mn.innex.stay.messaging.repo;

import java.time.Instant;
import java.util.UUID;

import mn.innex.stay.messaging.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @EntityGraph(attributePaths = "sender")
    Page<Message> findByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    /**
     * How many messages someone has not seen.
     *
     * <p>Their own messages never count: sending something does not leave it
     * unread, and a null {@code since} means they have never opened the thread.
     */
    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.sender.id <> :userId
              and (cast(:since as instant) is null or m.sentAt > :since)
            """)
    long countUnread(@Param("conversationId") UUID conversationId,
                     @Param("userId") UUID userId,
                     @Param("since") Instant since);

    /** The moderation queue: anything the detector was unhappy about. */
    @EntityGraph(attributePaths = {"sender", "conversation", "conversation.booking"})
    Page<Message> findByFlaggedReasonIsNotNullOrderBySentAtDesc(Pageable pageable);
}

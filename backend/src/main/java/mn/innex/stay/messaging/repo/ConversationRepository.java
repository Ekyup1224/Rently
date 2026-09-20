package mn.innex.stay.messaging.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.messaging.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByBookingId(UUID bookingId);

    /**
     * Someone's inbox, liveliest thread first.
     *
     * <p>Everything the inbox renders is fetched here. The service maps these to
     * a DTO inside its transaction, but the graph still matters: without it this
     * is a query per thread for the booking and another for its people.
     */
    @EntityGraph(attributePaths = {"booking", "booking.property", "booking.roomType",
            "booking.roomType.hotel", "booking.guest", "booking.host"})
    @Query("""
            select c from Conversation c
            join c.participants p
            where p.user.id = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    Page<Conversation> findForUser(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"participants", "booking", "booking.guest", "booking.host"})
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdWithParticipants(@Param("id") UUID id);
}

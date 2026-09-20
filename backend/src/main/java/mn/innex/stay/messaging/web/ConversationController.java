package mn.innex.stay.messaging.web;

import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.messaging.domain.Conversation;
import mn.innex.stay.messaging.domain.Message;
import mn.innex.stay.messaging.service.MessagingService;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Messages between a guest and their host.
 *
 * <p>The same endpoints serve both sides — who you are decides what you see, not
 * which URL you call — so there is one inbox rather than a guest one and a host
 * one that drift apart.
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final MessagingService messaging;

    public ConversationController(MessagingService messaging) {
        this.messaging = messaging;
    }

    @GetMapping
    public PageResponse<ConversationView> inbox(
            @PageableDefault(size = 25) Pageable pageable) {
        UUID actorId = CurrentActor.requireUserId();
        return PageResponse.of(messaging.inbox(actorId, pageable),
                entry -> ConversationView.of(entry.conversation(), actorId, entry.unread()));
    }

    /** Opens (or creates) the thread for a booking. */
    @PostMapping("/for-booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView openForBooking(@PathVariable UUID bookingId) {
        UUID actorId = CurrentActor.requireUserId();
        Conversation conversation = messaging.openForBooking(actorId, bookingId);
        return ConversationView.of(conversation, actorId, 0);
    }

    @GetMapping("/{conversationId}/messages")
    public PageResponse<MessageView> messages(@PathVariable UUID conversationId,
                                              @PageableDefault(size = 50) Pageable pageable) {
        UUID actorId = CurrentActor.requireUserId();
        return PageResponse.of(messaging.messages(actorId, conversationId, pageable),
                message -> MessageView.of(message, actorId));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@PathVariable UUID conversationId,
                            @Valid @RequestBody SendMessage request,
                            HttpServletRequest httpRequest) {
        UUID actorId = CurrentActor.requireUserId();
        Message message = messaging.send(actorId, conversationId, request.body(),
                ClientIp.of(httpRequest));
        return MessageView.of(message, actorId);
    }

    @PostMapping("/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID conversationId) {
        messaging.markRead(CurrentActor.requireUserId(), conversationId);
    }

    public record SendMessage(@NotBlank @Size(max = 4000) String body) {
    }

    /**
     * A thread as one participant sees it.
     *
     * @param withName the other person — an inbox listing your own name would be
     *                 useless
     */
    public record ConversationView(
            UUID id,
            UUID bookingId,
            String bookingReference,
            String listingTitle,
            String withName,
            java.time.LocalDate checkIn,
            java.time.LocalDate checkOut,
            long unread,
            Instant lastMessageAt) {

        static ConversationView of(Conversation conversation, UUID viewerId, long unread) {
            Booking booking = conversation.getBooking();
            boolean viewerIsGuest = booking.getGuest().getId().equals(viewerId);
            var other = viewerIsGuest ? booking.getHost() : booking.getGuest();

            return new ConversationView(conversation.getId(), booking.getId(),
                    booking.getReference(), titleOf(booking), firstName(other.getFullName()),
                    booking.getCheckIn(), booking.getCheckOut(), unread,
                    conversation.getLastMessageAt());
        }

        private static String titleOf(Booking booking) {
            if (booking.isHotelStay()) {
                return booking.getRoomType().getHotel().getName();
            }
            return booking.getProperty() == null ? "Stay" : booking.getProperty().getTitle();
        }

        private static String firstName(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "Guest";
            }
            return fullName.trim().split("\\s+")[0];
        }
    }

    /**
     * @param mine so a client can align the bubble without comparing ids
     * @param flaggedReason shown only to the sender: telling the recipient the
     *                      message was flagged would teach a scammer exactly
     *                      which words to avoid
     */
    public record MessageView(UUID id, String body, boolean mine, String senderName,
                              String flaggedReason, Instant sentAt) {

        static MessageView of(Message message, UUID viewerId) {
            boolean mine = message.getSender().getId().equals(viewerId);
            return new MessageView(message.getId(), message.getBody(), mine,
                    message.getSender().getFullName(),
                    mine ? message.getFlaggedReason() : null,
                    message.getSentAt());
        }
    }
}

package mn.innex.stay.messaging.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.messaging.domain.Conversation;
import mn.innex.stay.messaging.domain.ConversationParticipant;
import mn.innex.stay.messaging.domain.Message;
import mn.innex.stay.messaging.repo.ConversationRepository;
import mn.innex.stay.messaging.repo.MessageRepository;
import mn.innex.stay.trust.domain.FlagType;
import mn.innex.stay.trust.service.ListingFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversations between a guest and their host.
 *
 * <p>A thread exists for a booking and only for a booking, from the moment it is
 * made until a fortnight after checkout. That window is what keeps this from
 * becoming a channel for reaching strangers: there is no way to message someone
 * you have no stay with, and nothing to reach once the stay is long past.
 */
@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    /** How long after checkout a thread stays open. */
    private static final long OPEN_DAYS_AFTER_CHECKOUT = 14;

    /** A stay that is off entirely: nothing left to discuss. */
    private static final List<BookingStatus> CLOSED = List.of(
            BookingStatus.DECLINED, BookingStatus.EXPIRED);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final BookingRepository bookings;
    private final OffPlatformDetector detector;
    private final ListingFlagService flags;
    private final AuditService auditService;

    public MessagingService(ConversationRepository conversations, MessageRepository messages,
                            BookingRepository bookings, OffPlatformDetector detector,
                            ListingFlagService flags, AuditService auditService) {
        this.conversations = conversations;
        this.messages = messages;
        this.bookings = bookings;
        this.detector = detector;
        this.flags = flags;
        this.auditService = auditService;
    }

    /**
     * The thread for a booking, created on first use.
     *
     * @throws ApiException 404 when the booking is not theirs
     */
    @Transactional
    public Conversation openForBooking(UUID actorId, UUID bookingId) {
        Booking booking = bookings.findByIdWithDetails(bookingId)
                .orElseThrow(() -> ApiException.notFound("booking_not_found", "No such booking"));
        requireParticipant(booking, actorId);

        return conversations.findByBookingId(bookingId).orElseGet(() -> {
            Conversation conversation = new Conversation(booking);
            conversations.saveAndFlush(conversation);
            conversation.addParticipant(new ConversationParticipant(conversation,
                    booking.getGuest()));
            conversation.addParticipant(new ConversationParticipant(conversation,
                    booking.getHost()));
            return conversations.save(conversation);
        });
    }

    /**
     * Someone's inbox, with each thread's unread count already worked out.
     *
     * <p>The count is computed here rather than by the caller because it needs
     * that viewer's {@code lastReadAt}, which lives on a lazy collection — and a
     * controller reaching for it after the transaction has closed is a 500, not
     * a second query. Returning the number rather than the row it came from
     * keeps that inside the session for good.
     */
    @Transactional(readOnly = true)
    public Page<InboxEntry> inbox(UUID userId, Pageable pageable) {
        return conversations.findForUser(userId, pageable).map(conversation -> {
            Instant lastRead = conversation.getParticipants().stream()
                    .filter(participant -> participant.getUser().getId().equals(userId))
                    .findFirst()
                    .map(ConversationParticipant::getLastReadAt)
                    .orElse(null);
            return new InboxEntry(conversation,
                    messages.countUnread(conversation.getId(), userId, lastRead));
        });
    }

    /** One row of an inbox: the thread, and how much of it this viewer has not read. */
    public record InboxEntry(Conversation conversation, long unread) {
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID conversationId, UUID userId, Instant since) {
        return messages.countUnread(conversationId, userId, since);
    }

    @Transactional(readOnly = true)
    public Page<Message> messages(UUID actorId, UUID conversationId, Pageable pageable) {
        requireMembership(actorId, conversationId);
        return messages.findByConversationIdOrderBySentAtDesc(conversationId, pageable);
    }

    /**
     * Sends a message, and quietly reports it if it looks like an attempt to move
     * payment off the platform.
     *
     * <p>The message goes through either way. A false positive must not eat
     * somebody's words, and the flag exists so a person can look — the same queue
     * the duplicate-photo and guest-report flags land in.
     */
    @Transactional
    public Message send(UUID senderId, UUID conversationId, String body, String ip) {
        Conversation conversation = requireMembership(senderId, conversationId);
        Booking booking = conversation.getBooking();
        assertOpen(booking);

        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("empty_message", "Write something first");
        }

        Optional<String> concern = detector.inspect(trimmed);
        var sender = booking.getGuest().getId().equals(senderId)
                ? booking.getGuest() : booking.getHost();

        Message message = messages.save(
                new Message(conversation, sender, trimmed, concern.orElse(null)));
        conversation.touch(message.getSentAt());
        conversations.save(conversation);

        concern.ifPresent(reason -> raiseFlag(booking, sender.getId(), reason, message, ip));

        auditService.record(senderId, AuditAction.MESSAGE_SENT, "Conversation", conversationId,
                Map.of("flagged", String.valueOf(concern.isPresent())), ip);
        return message;
    }

    @Transactional
    public void markRead(UUID actorId, UUID conversationId) {
        Conversation conversation = requireMembership(actorId, conversationId);
        conversation.getParticipants().stream()
                .filter(participant -> participant.getUser().getId().equals(actorId))
                .findFirst()
                .ifPresent(participant -> participant.markRead(Instant.now()));
        conversations.save(conversation);
    }

    @Transactional(readOnly = true)
    public Page<Message> flagged(Pageable pageable) {
        return messages.findByFlaggedReasonIsNotNullOrderBySentAtDesc(pageable);
    }

    /**
     * Reports a message that reads like an off-platform payment request.
     *
     * <p>Only when the host sent it. A guest asking to pay in cash is a guest
     * making a poor decision; a host asking for it is the platform's problem, and
     * the one worth freezing money over.
     */
    private void raiseFlag(Booking booking, UUID senderId, String reason, Message message,
                           String ip) {
        log.warn("Message {} flagged as {}", message.getId(), reason);
        auditService.record(senderId, AuditAction.MESSAGE_FLAGGED, "Message", message.getId(),
                Map.of("reason", reason), ip);

        if (!booking.getHost().getId().equals(senderId)) {
            return;
        }

        SupplyKind kind = booking.isHotelStay() ? SupplyKind.HOTEL : SupplyKind.PROPERTY;
        UUID supplyId = booking.isHotelStay()
                ? booking.getRoomType().getHotel().getId()
                : booking.getProperty().getId();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("messageId", message.getId().toString());
        details.put("bookingReference", booking.getReference());
        // The words themselves, because a reviewer cannot judge this without them.
        details.put("excerpt", message.getBody().length() > 400
                ? message.getBody().substring(0, 400) + "…"
                : message.getBody());

        flags.raise(kind, supplyId, FlagType.GUEST_REPORT, null, booking.getId(),
                "A host asked to be paid outside the platform", details);
    }

    private void assertOpen(Booking booking) {
        if (CLOSED.contains(booking.getStatus())) {
            throw ApiException.conflict("conversation_closed",
                    "This booking is over; there is nothing to discuss");
        }
        if (PlatformTime.today().isAfter(
                booking.getCheckOut().plusDays(OPEN_DAYS_AFTER_CHECKOUT))) {
            throw ApiException.conflict("conversation_closed",
                    "Messages close " + OPEN_DAYS_AFTER_CHECKOUT + " days after checkout");
        }
    }

    private Conversation requireMembership(UUID actorId, UUID conversationId) {
        Conversation conversation = conversations.findByIdWithParticipants(conversationId)
                .orElseThrow(() -> ApiException.notFound("conversation_not_found",
                        "No such conversation"));
        boolean member = conversation.getParticipants().stream()
                .anyMatch(participant -> participant.getUser().getId().equals(actorId));
        if (!member) {
            // Not "forbidden": whether a thread exists is not their business.
            throw ApiException.notFound("conversation_not_found", "No such conversation");
        }
        return conversation;
    }

    private void requireParticipant(Booking booking, UUID actorId) {
        if (!booking.getGuest().getId().equals(actorId)
                && !booking.getHost().getId().equals(actorId)) {
            throw ApiException.notFound("booking_not_found", "No such booking");
        }
    }
}

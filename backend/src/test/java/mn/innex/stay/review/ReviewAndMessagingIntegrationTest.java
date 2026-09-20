package mn.innex.stay.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.IntegrationTest;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyPhoto;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.messaging.repo.MessageRepository;
import mn.innex.stay.messaging.service.MessagingService;
import mn.innex.stay.review.repo.ReviewRepository;
import mn.innex.stay.review.service.ReviewService;
import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.repo.ListingFlagRepository;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * What guests and hosts say to and about each other.
 *
 * <p>The two features share a test because they share a purpose: both are only
 * worth having if they are honest. A review anyone can read before writing their
 * own becomes a negotiation rather than a record, and a message thread that
 * quietly lets a host ask for a bank transfer undoes every hold in the trust
 * module.
 */
class ReviewAndMessagingIntegrationTest extends IntegrationTest {


    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReviewService reviews;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MessagingService messaging;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ListingFlagRepository flagRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User owner;
    private User guest;
    private Property listing;

    @BeforeEach
    void setUp() {
        owner = createUser(Role.HOUSE_OWNER, "Bat Erdene");
        guest = createUser(Role.CLIENT, "Saraa Dorj");

        listing = new Property(owner, "Review test cabin", PropertyType.HOUSE, "Ulaanbaatar");
        listing.setDescription("A cabin used by the review tests.");
        listing.setAddressLine("Terelj road, km 14");
        listing.setDistrict("Bayanzurkh");
        listing.setLocation(47.9812, 107.4501);
        listing.setMaxGuests(4);
        listing.setBasePrice(new BigDecimal("150000"));
        listing.setMinStayNights(1);
        listing.setInstantBook(true);
        listing.addPhoto(new PropertyPhoto(listing,
                "properties/fixture/" + java.util.UUID.randomUUID() + ".jpg",
                "image/jpeg", 2048, "Cover", 0));
        listing.approve();
        propertyRepository.saveAndFlush(listing);
    }

    // --- reviews ---------------------------------------------------------

    @Test
    @DisplayName("a stay that has not happened yet cannot be reviewed")
    void cannotReviewBeforeTheStay() {
        Booking booking = bookingService.confirmPaid(bookingService.create(guest.getId(),
                listing.getId(), PlatformTime.today().plusDays(5),
                PlatformTime.today().plusDays(7), 2, null, null).getId());

        assertThatThrownBy(() -> reviews.write(guest.getId(), booking.getId(), 5,
                Map.of(), "Lovely", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only be reviewed once it is over");
    }

    @Test
    @DisplayName("the first review written stays hidden until the other side answers")
    void firstReviewIsBlind() {
        Booking booking = finishedStay();

        var written = reviews.write(guest.getId(), booking.getId(), 5,
                Map.of("cleanliness", 5, "location", 4), "Warm and spotless.", null);

        assertThat(written.isVisible())
                .as("a host who can read the guest's review before writing their own "
                        + "is writing a reply, not a review")
                .isFalse();
        assertThat(reviews.forSupply(listing.getId(), PageRequest.of(0, 10)))
                .as("and nothing hidden may appear on the listing page")
                .isEmpty();
    }

    @Test
    @DisplayName("both reviews appear at the same moment, once both are in")
    void bothPublishTogether() {
        Booking booking = finishedStay();
        reviews.write(guest.getId(), booking.getId(), 5, Map.of(), "Warm and spotless.", null);

        reviews.write(owner.getId(), booking.getId(), 4, Map.of(), "Tidy guests.", null);

        assertThat(reviews.forBooking(booking.getId()))
                .hasSize(2)
                .allMatch(review -> review.isVisible());
    }

    @Test
    @DisplayName("a stay nobody reviewed back publishes when the window closes")
    void unansweredReviewPublishesOnExpiry() {
        Booking booking = finishedStay();
        var written = reviews.write(guest.getId(), booking.getId(), 5, Map.of(), "Great.", null);
        // The sweep works on age, so age the row rather than the clock.
        ageReview(written.getId(), 15);

        assertThat(reviews.publishExpired()).isGreaterThanOrEqualTo(1);

        assertThat(reviewRepository.findById(written.getId()).orElseThrow().isVisible())
                .as("a host declining to reply must not be able to bury a review")
                .isTrue();
    }

    @Test
    @DisplayName("nobody reviews the same stay twice")
    void oneReviewPerPersonPerStay() {
        Booking booking = finishedStay();
        reviews.write(guest.getId(), booking.getId(), 5, Map.of(), "Great.", null);

        assertThatThrownBy(() -> reviews.write(guest.getId(), booking.getId(), 1,
                Map.of(), "Actually, no.", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    @DisplayName("a stranger cannot review a stay they were not on")
    void onlyTheTwoPartiesMayReview() {
        Booking booking = finishedStay();
        User stranger = createUser(Role.CLIENT, "Passing By");

        assertThatThrownBy(() -> reviews.write(stranger.getId(), booking.getId(), 1,
                Map.of(), "Never went.", null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a published review moves the listing's rating")
    void publishedReviewUpdatesTheRating() {
        Booking booking = finishedStay();
        reviews.write(guest.getId(), booking.getId(), 4, Map.of(), "Good.", null);
        reviews.write(owner.getId(), booking.getId(), 5, Map.of(), "Good guests.", null);

        Property updated = propertyRepository.findById(listing.getId()).orElseThrow();
        assertThat(updated.getRatingCount()).isEqualTo(1);
        assertThat(updated.getRatingAverage()).isEqualByComparingTo("4.0");
        // The host's review is of the guest, so it must not touch the listing.
    }

    @Test
    @DisplayName("a hidden review reports itself unreadable, not merely published")
    void hidingMakesAReviewUnreadable() {
        Booking booking = finishedStay();
        var written = reviews.write(guest.getId(), booking.getId(), 5, Map.of(), "Great.", null);
        reviews.write(owner.getId(), booking.getId(), 5, Map.of(), "Great guests.", null);

        var hidden = reviews.moderate(createUser(Role.SUPER_ADMIN, "Admin").getId(),
                written.getId(), true, "Names another guest", null);

        // Two separate flags back this: the blind period is over, but a moderator
        // has taken it down. Reporting only the first tells every client it is
        // still on the listing.
        assertThat(hidden.isReadable()).isFalse();
        assertThat(reviews.forSupply(listing.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(propertyRepository.findById(listing.getId()).orElseThrow().getRatingCount())
                .as("and it stops counting towards the rating")
                .isZero();
    }

    @Test
    @DisplayName("a host cannot reply underneath a review that has been taken down")
    void cannotRespondToAHiddenReview() {
        Booking booking = finishedStay();
        var written = reviews.write(guest.getId(), booking.getId(), 2, Map.of(), "Cold.", null);
        reviews.write(owner.getId(), booking.getId(), 5, Map.of(), "Fine.", null);
        reviews.moderate(createUser(Role.SUPER_ADMIN, "Admin").getId(), written.getId(),
                true, "Off-topic", null);

        assertThatThrownBy(() -> reviews.respond(owner.getId(), written.getId(), "Not so.", null))
                .as("replying under a hidden review would republish the accusation")
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("once the review is published");
    }

    // --- messaging -------------------------------------------------------

    @Test
    @DisplayName("a booking gets one thread, however often it is opened")
    void threadIsCreatedOncePerBooking() {
        Booking booking = paidStay();

        var first = messaging.openForBooking(guest.getId(), booking.getId());
        var second = messaging.openForBooking(owner.getId(), booking.getId());

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("only the guest and the host can read the thread")
    void outsidersCannotReadTheThread() {
        Booking booking = paidStay();
        var conversation = messaging.openForBooking(guest.getId(), booking.getId());
        User stranger = createUser(Role.CLIENT, "Passing By");

        assertThatThrownBy(() -> messaging.messages(stranger.getId(), conversation.getId(),
                PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("an ordinary message goes through untouched")
    void normalMessageIsNotFlagged() {
        Booking booking = paidStay();
        var conversation = messaging.openForBooking(guest.getId(), booking.getId());

        var sent = messaging.send(guest.getId(), conversation.getId(),
                "Сайн байна уу, бид 6 цагт ирнэ.", null);

        assertThat(sent.getFlaggedReason()).isNull();
    }

    @Test
    @DisplayName("a host asking for a bank transfer is flagged and their money frozen")
    void offPlatformRequestRaisesAFlag() {
        Booking booking = paidStay();
        var conversation = messaging.openForBooking(owner.getId(), booking.getId());

        var sent = messaging.send(owner.getId(), conversation.getId(),
                "Хаанбанк 5301234567 руу шилжүүлээрэй, тэгвэл хямд болно", null);

        assertThat(sent.getFlaggedReason()).isNotNull();
        assertThat(messageRepository.findById(sent.getId()).orElseThrow().getBody())
                .as("the guest must still see it — hiding the attempt hides the evidence")
                .isNotBlank();
        assertThat(flagRepository.findAll().stream()
                .filter(flag -> flag.getSupplyId().equals(listing.getId()))
                .filter(flag -> flag.getStatus() == FlagStatus.OPEN))
                .as("the same listing flag that freezes payouts")
                .isNotEmpty();
    }

    @Test
    @DisplayName("a guest naming a bank does not freeze the host's money")
    void guestMessagesDoNotFlagTheHost() {
        Booking booking = paidStay();
        var conversation = messaging.openForBooking(guest.getId(), booking.getId());

        messaging.send(guest.getId(), conversation.getId(),
                "Хаанбанк 5301234567 данснаас төлсөн, баталгаа хэрэгтэй юу?", null);

        assertThat(flagRepository.findAll().stream()
                .filter(flag -> flag.getSupplyId().equals(listing.getId())))
                .as("only the side that gets paid can be trying to get paid elsewhere")
                .isEmpty();
    }

    @Test
    @DisplayName("the inbox carries each thread's unread count with it")
    void inboxReportsUnread() {
        Booking booking = paidStay();
        var conversation = messaging.openForBooking(guest.getId(), booking.getId());
        messaging.send(owner.getId(), conversation.getId(), "Түлхүүр хаалганы хажууд", null);
        messaging.send(owner.getId(), conversation.getId(), "Гэрлийн залгуур нь зүүн талд", null);

        var inbox = messaging.inbox(guest.getId(), PageRequest.of(0, 10));

        // The count needs this viewer's lastReadAt, which lives on a lazy
        // collection — computing it anywhere but inside the query's transaction
        // is a 500 rather than a number.
        assertThat(inbox.getContent()).hasSize(1);
        assertThat(inbox.getContent().getFirst().unread()).isEqualTo(2);
        assertThat(inbox.getContent().getFirst().conversation().getBooking().getReference())
                .isEqualTo(booking.getReference());

        messaging.markRead(guest.getId(), conversation.getId());
        assertThat(messaging.inbox(guest.getId(), PageRequest.of(0, 10))
                .getContent().getFirst().unread()).isZero();
    }

    @Test
    @DisplayName("an inbox shows only your own threads")
    void inboxIsPrivate() {
        Booking booking = paidStay();
        messaging.openForBooking(guest.getId(), booking.getId());
        User stranger = createUser(Role.CLIENT, "Passing By");

        assertThat(messaging.inbox(stranger.getId(), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("unread counts fall to zero once the thread is opened")
    void readingClearsTheUnreadCount() {
        Booking booking = paidStay();
        var conversation = messaging.openForBooking(guest.getId(), booking.getId());
        messaging.send(owner.getId(), conversation.getId(), "Түлхүүр хаалганы хажууд байна", null);

        messaging.markRead(guest.getId(), conversation.getId());

        assertThat(messaging.unreadCount(conversation.getId(), guest.getId(),
                java.time.Instant.now())).isZero();
    }

    // --- fixtures --------------------------------------------------------

    /** A paid, confirmed stay in the future. */
    private Booking paidStay() {
        LocalDate checkIn = PlatformTime.today().plusDays(4);
        return bookingService.confirmPaid(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkIn.plusDays(2), 2, null, null).getId());
    }

    /** A stay that has been and gone, so it can be reviewed. */
    private Booking finishedStay() {
        LocalDate checkIn = PlatformTime.today();
        Booking booking = bookingService.confirmPaid(bookingService.create(guest.getId(),
                listing.getId(), checkIn, checkIn.plusDays(1), 2, null, null).getId());
        bookingService.checkIn(owner.getId(), booking.getId(), null);
        return bookingService.checkOut(owner.getId(), booking.getId(), null);
    }

    /** Backdates a review so the publication sweep considers it. */
    private void ageReview(java.util.UUID reviewId, int days) {
        jdbc.update("update reviews set created_at = created_at - make_interval(days => ?) "
                + "where id = ?", days, reviewId);
    }

    private User createUser(Role role, String name) {
        User user = User.createWithPhone(uniquePhone(), "mn");
        user.markPhoneVerified();
        user.setFullName(name);
        user.grantRole(Role.CLIENT, null, null);
        if (role != Role.CLIENT) {
            user.grantRole(role, null, null);
        }
        return userRepository.saveAndFlush(user);
    }
}

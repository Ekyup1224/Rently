package mn.innex.stay.trust;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import mn.innex.stay.IntegrationTest;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyPhoto;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.domain.FlagType;
import mn.innex.stay.trust.domain.PayoutStatus;
import mn.innex.stay.trust.repo.PayoutRepository;
import mn.innex.stay.trust.service.ListingFlagService;
import mn.innex.stay.trust.service.PayoutService;
import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import mn.innex.stay.common.PlatformTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The rules that decide whether a host's money may move.
 *
 * <p>This is the part of the product that makes a fake listing unprofitable, so
 * the cases worth pinning are the refusals: money must not leave before someone
 * has actually arrived, before the payee is a known person, or while anyone has
 * said the listing is wrong.
 */
@SpringBootTest(properties = "app.trust.payout-hold=0s")
class PayoutHoldIntegrationTest extends IntegrationTest {


    @Autowired
    private BookingService bookingService;

    @Autowired
    private PayoutService payouts;

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private ListingFlagService flags;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User guest;
    private Property listing;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        owner = createUser(Role.HOUSE_OWNER);
        guest = createUser(Role.CLIENT);

        listing = new Property(owner, "Payout test ger", PropertyType.GER, "Ulaanbaatar");
        listing.setDescription("A ger for payout tests.");
        listing.setAddressLine("Gachuurt valley, plot 2");
        listing.setDistrict("Bayanzurkh");
        listing.setLocation(47.9405, 107.1210);
        listing.setMaxGuests(4);
        listing.setBasePrice(new BigDecimal("180000"));
        listing.setMinStayNights(1);
        // Instant book, so a booking reaches PENDING_PAYMENT without a host decision:
        // this test is about what happens after the money arrives.
        listing.setInstantBook(true);
        // A CHECK constraint requires a complete, locatable listing before APPROVED.
        listing.addPhoto(new PropertyPhoto(listing,
                "properties/fixture/" + java.util.UUID.randomUUID() + ".jpg",
                "image/jpeg", 2048, "Cover", 0));
        listing.approve();
        propertyRepository.saveAndFlush(listing);

        checkIn = PlatformTime.today().plusDays(10);
        checkOut = checkIn.plusDays(2);
    }

    @Test
    @DisplayName("settling a payment schedules the payout behind a hold, it does not pay it")
    void settlementSchedulesRatherThanPays() {
        Booking booking = payFor(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null));

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        assertThat(payout.getAmount()).isEqualByComparingTo(booking.getHostPayout());
        // Check-in day plus the 24-hour hold, so a stay ten days out is not due now.
        assertThat(payout.isDue(java.time.Instant.now())).isFalse();
    }

    @Test
    @DisplayName("a replayed payment callback cannot schedule a second payout")
    void schedulingIsIdempotent() {
        Booking booking = payFor(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null));

        payouts.schedule(booking);
        payouts.schedule(booking);

        assertThat(payoutRepository.findAll().stream()
                .filter(payout -> payout.getBooking().getId().equals(booking.getId()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("nobody checked in, so the money stays put even once the hold expires")
    void doesNotReleaseWhenTheGuestNeverArrived() {
        Booking booking = dueBooking();
        verify(owner);

        payouts.releaseDue();

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        // The clearest signal a listing was never real.
        assertThat(payout.getBlockedReason()).isEqualTo("stay_not_started");
    }

    @Test
    @DisplayName("an unverified host is not paid, however real the stay was")
    void doesNotReleaseWithoutIdentity() {
        Booking booking = dueBooking();
        bookingService.checkIn(owner.getId(), booking.getId(), null);

        payouts.releaseDue();

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout.getBlockedReason()).isEqualTo("kyc_required");
    }

    @Test
    @DisplayName("arrived, verified and unflagged: the money is released")
    void releasesWhenEverythingChecksOut() {
        Booking booking = dueBooking();
        bookingService.checkIn(owner.getId(), booking.getId(), null);
        verify(owner);

        assertThat(payouts.releaseDue()).isEqualTo(1);

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RELEASED);
        assertThat(payout.getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("a guest report freezes the money the moment it is filed")
    void reportFreezesPayout() {
        Booking booking = payFor(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null));

        flags.raise(SupplyKind.PROPERTY, listing.getId(), FlagType.GUEST_REPORT, guest.getId(),
                booking.getId(), "There is no house at this address", Map.of());

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout.getBlockedReason()).isEqualTo("listing_flagged");
    }

    @Test
    @DisplayName("an open flag keeps the money held even after a real check-in")
    void flaggedListingIsNotPaidOut() {
        Booking booking = dueBooking();
        bookingService.checkIn(owner.getId(), booking.getId(), null);
        verify(owner);
        flags.raise(SupplyKind.PROPERTY, listing.getId(), FlagType.DUPLICATE_PHOTO, null, null,
                "A photo here belongs to another account", Map.of());

        payouts.releaseDue();

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
    }

    @Test
    @DisplayName("dismissing the last flag returns the money to waiting, not to released")
    void dismissingUnfreezesButDoesNotApprove() {
        Booking booking = payFor(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null));
        var flag = flags.raise(SupplyKind.PROPERTY, listing.getId(), FlagType.GUEST_REPORT,
                guest.getId(), booking.getId(), "Wrong address", Map.of());

        flags.resolve(createUser(Role.SUPER_ADMIN).getId(), flag.getId(), FlagStatus.DISMISSED,
                "Guest had the wrong listing", null);

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus())
                .as("unfreezing returns it to the queue; the release rules still have to pass")
                .isEqualTo(PayoutStatus.PENDING);
    }

    @Test
    @DisplayName("upholding a flag takes the listing off sale, not just the money")
    void upheldFlagSuspendsTheListing() {
        var flag = flags.raise(SupplyKind.PROPERTY, listing.getId(), FlagType.DUPLICATE_PHOTO,
                null, null, "A photo here belongs to another account", Map.of());

        flags.resolve(createUser(Role.SUPER_ADMIN).getId(), flag.getId(), FlagStatus.UPHELD,
                "Confirmed stolen", null);

        // Freezing the money alone would leave a listing known to be fake still
        // taking bookings, costing guests trips nobody is ever paid for.
        assertThat(propertyRepository.findById(listing.getId()).orElseThrow().getStatus())
                .isEqualTo(SupplyStatus.SUSPENDED);
    }

    @Test
    @DisplayName("cancelling the stay cancels what was owed for it")
    void cancellationVoidsThePayout() {
        Booking booking = payFor(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null));

        bookingService.cancelByGuest(guest.getId(), booking.getId(), "changed plans", null);

        var payout = payoutRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CANCELLED);
    }

    /**
     * A paid booking the sweep will consider now.
     *
     * <p>Check-in today, and this class runs with a zero hold, so release_after is
     * the start of today — already past. Cleaner than moving the clock, and it
     * exercises the same code path a real 24-hour hold reaches tomorrow.
     */
    private Booking dueBooking() {
        checkIn = PlatformTime.today();
        checkOut = checkIn.plusDays(2);
        Booking booking = payFor(bookingService.create(guest.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null));
        assertThat(payoutRepository.findByBookingId(booking.getId()).orElseThrow()
                .isDue(java.time.Instant.now())).isTrue();
        return booking;
    }

    private Booking payFor(Booking booking) {
        return bookingService.confirmPaid(booking.getId());
    }

    private void verify(User user) {
        user.setKycStatus(KycStatus.VERIFIED);
        userRepository.saveAndFlush(user);
    }

    private User createUser(Role role) {
        User user = User.createWithPhone(uniquePhone(), "mn");
        user.markPhoneVerified();
        user.setFullName("Payout Test Person");
        user.grantRole(Role.CLIENT, null, null);
        if (role != Role.CLIENT) {
            user.grantRole(role, null, null);
        }
        return userRepository.saveAndFlush(user);
    }
}

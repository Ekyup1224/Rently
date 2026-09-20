package mn.innex.stay.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import mn.innex.stay.IntegrationTest;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyPhoto;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.trust.domain.PayoutBatchStatus;
import mn.innex.stay.trust.domain.PayoutStatus;
import mn.innex.stay.trust.repo.PayoutRepository;
import mn.innex.stay.trust.service.PayoutBatchService;
import mn.innex.stay.trust.service.PayoutService;
import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * Turning what is owed into what gets sent.
 *
 * <p>Batching exists because the last step is manual: somebody uploads a file to
 * a bank. So the thing worth testing is that the file is right — one line per
 * host no matter how many stays, nothing in it that was not cleared for release,
 * and no way to send the same run twice.
 */
@SpringBootTest(properties = "app.trust.payout-hold=0s")
class PayoutBatchIntegrationTest extends IntegrationTest {

    /** Distinguishes fixture names within this class; phones come from the base. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);


    @Autowired
    private BookingService bookingService;

    @Autowired
    private PayoutService payouts;

    @Autowired
    private PayoutBatchService batches;

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User guest;
    private Property listing;

    @BeforeEach
    void setUp() {
        owner = createUser(Role.HOUSE_OWNER, "Batbayar Owner");
        guest = createUser(Role.CLIENT, "Guest Person");
        owner.setKycStatus(KycStatus.VERIFIED);
        userRepository.saveAndFlush(owner);
        listing = approvedListing();
    }

    @Test
    @DisplayName("there is nothing to send when nothing has been released")
    void refusesToAssembleAnEmptyRun() {
        assertThatThrownBy(() -> batches.assemble(owner.getId(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No released payouts");
    }

    @Test
    @DisplayName("two stays with the same host make one transfer line, not two")
    void groupsPerHost() {
        releasedStay();
        releasedStay();

        var batch = batches.assemble(admin(), null);
        var lines = batches.transferLines(batch.getId());

        assertThat(batch.getPayoutCount()).isEqualTo(2);
        assertThat(lines)
                .as("a host with eleven stays should see one line on their statement")
                .hasSize(1);
        assertThat(lines.getFirst().bookings()).hasSize(2);
        assertThat(lines.getFirst().amount())
                .isEqualByComparingTo(batch.getTotal());
        assertThat(lines.getFirst().payeeName()).isEqualTo("Batbayar Owner");
    }

    @Test
    @DisplayName("held money stays out of the run")
    void onlyReleasedPayoutsAreBatched() {
        var released = releasedStay();
        // Paid but nobody has arrived yet, so this one is still on hold.
        var held = paidStay(PlatformTime.today().plusDays(20));

        var batch = batches.assemble(admin(), null);

        assertThat(batch.getPayoutCount()).isEqualTo(1);
        assertThat(payoutRepository.findByBookingId(held.getId()).orElseThrow().getBatch())
                .as("money that has not cleared its checks must never reach a bank file")
                .isNull();
        assertThat(payoutRepository.findByBookingId(released.getId()).orElseThrow().getBatch())
                .isNotNull();
    }

    @Test
    @DisplayName("a second run does not pick up what the first one took")
    void assembledPayoutsAreNotBatchedAgain() {
        releasedStay();
        batches.assemble(admin(), null);

        assertThatThrownBy(() -> batches.assemble(admin(), null))
                .as("otherwise a host is paid twice for one stay")
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No released payouts");
    }

    @Test
    @DisplayName("a run can only be exported once")
    void exportIsNotRepeatable() {
        releasedStay();
        var batch = batches.assemble(admin(), null);

        var exported = batches.markExported(admin(), batch.getId(), null);
        assertThat(exported.getStatus()).isEqualTo(PayoutBatchStatus.EXPORTED);
        assertThat(exported.getExportedAt()).isNotNull();

        assertThatThrownBy(() -> batches.markExported(admin(), batch.getId(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already been exported");
    }

    @Test
    @DisplayName("settling the run marks every payout in it paid, against one reference")
    void settlementMarksEveryMember() {
        var first = releasedStay();
        var second = releasedStay();
        var batch = batches.assemble(admin(), null);
        batches.markExported(admin(), batch.getId(), null);

        batches.settle(admin(), batch.getId(), "KHAN-2026-0913-01", null);

        assertThat(payoutRepository.findByBookingId(first.getId()).orElseThrow())
                .satisfies(payout -> {
                    assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
                    assertThat(payout.getProviderRef()).isEqualTo("KHAN-2026-0913-01");
                    assertThat(payout.getPaidAt()).isNotNull();
                });
        assertThat(payoutRepository.findByBookingId(second.getId()).orElseThrow().getStatus())
                .isEqualTo(PayoutStatus.PAID);
        assertThat(batches.list(PageRequest.of(0, 5)).getContent().getFirst().getStatus())
                .isEqualTo(PayoutBatchStatus.SETTLED);
    }

    @Test
    @DisplayName("a settled run cannot be settled again")
    void settlementIsNotRepeatable() {
        releasedStay();
        var batch = batches.assemble(admin(), null);
        batches.settle(admin(), batch.getId(), "KHAN-1", null);

        assertThatThrownBy(() -> batches.settle(admin(), batch.getId(), "KHAN-2", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already settled");
    }

    // --- fixtures --------------------------------------------------------

    /** A stay whose payout has passed every check and been released. */
    private Booking releasedStay() {
        Booking booking = paidStay(PlatformTime.today());
        bookingService.checkIn(owner.getId(), booking.getId(), null);
        payouts.releaseDue();
        assertThat(payoutRepository.findByBookingId(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(PayoutStatus.RELEASED);
        return booking;
    }

    private Booking paidStay(LocalDate checkIn) {
        // A fresh listing each time: one property cannot hold two overlapping stays.
        Property target = listing.getStatus() == null ? listing : approvedListing();
        return bookingService.confirmPaid(bookingService.create(guest.getId(), target.getId(),
                checkIn, checkIn.plusDays(1), 2, null, null).getId());
    }

    private Property approvedListing() {
        Property property = new Property(owner, "Batch test house " + SEQUENCE.incrementAndGet(),
                PropertyType.HOUSE, "Ulaanbaatar");
        property.setDescription("A house used by the payout batch tests.");
        property.setAddressLine("Zaisan hill, plot 8");
        property.setDistrict("Khan-Uul");
        property.setLocation(47.8879, 106.9142);
        property.setMaxGuests(4);
        property.setBasePrice(new BigDecimal("200000"));
        property.setMinStayNights(1);
        property.setInstantBook(true);
        property.addPhoto(new PropertyPhoto(property,
                "properties/fixture/" + java.util.UUID.randomUUID() + ".jpg",
                "image/jpeg", 2048, "Cover", 0));
        property.approve();
        return propertyRepository.saveAndFlush(property);
    }

    private java.util.UUID admin() {
        return createUser(Role.SUPER_ADMIN, "Admin Person").getId();
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

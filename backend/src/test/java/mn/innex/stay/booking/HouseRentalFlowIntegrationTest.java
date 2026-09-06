package mn.innex.stay.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import mn.innex.stay.IntegrationTest;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.listing.domain.CancellationPolicy;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyPhoto;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The house rental loop over HTTP, plus the guarantees that only concurrency reveals. */
class HouseRentalFlowIntegrationTest extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(70_000_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    private User owner;
    private User guest;
    private Property listing;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        owner = createUser(Role.HOUSE_OWNER);
        guest = createUser(Role.CLIENT);
        listing = createApprovedListing(owner);
        checkIn = LocalDate.now(mn.innex.stay.common.PlatformTime.ZONE).plusDays(45);
        checkOut = checkIn.plusDays(3);
    }

    @Test
    @DisplayName("a guest can find, price and book a listing")
    void guestCanBookAListing() throws Exception {
        mockMvc.perform(get("/api/v1/listings/search").param("city", "Ulaanbaatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.id == '" + listing.getId() + "')]").exists());

        mockMvc.perform(post("/api/v1/listings/{id}/quote", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(checkIn, checkOut, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nights").value(3))
                .andExpect(jsonPath("$.nightlySubtotal").value(540000.00))
                .andExpect(jsonPath("$.total").value(565000.00))
                // Host-side economics are not the guest's business.
                .andExpect(jsonPath("$.hostPayout").doesNotExist());

        mockMvc.perform(post("/api/v1/bookings")
                        .with(as(guest, "ROLE_CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(listing.getId(), checkIn, checkOut, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_HOST_APPROVAL"))
                .andExpect(jsonPath("$.total").value(565000.00));
    }

    @Test
    @DisplayName("the quote a guest is shown is the price the booking is made at")
    void quoteMatchesBookedPrice() throws Exception {
        String quote = mockMvc.perform(post("/api/v1/listings/{id}/quote", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(checkIn, checkOut, 2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        BigDecimal quotedTotal = new BigDecimal(
                com.jayway.jsonpath.JsonPath.read(quote, "$.total").toString());

        var booking = bookingService.create(guest.getId(), listing.getId(), checkIn, checkOut,
                2, null, null);
        assertThat(booking.getTotal()).isEqualByComparingTo(quotedTotal);
    }

    @Test
    @DisplayName("a listing that is not approved is invisible and unbookable")
    void unapprovedListingIsInvisible() throws Exception {
        Property draft = new Property(owner, "Unfinished draft", PropertyType.APARTMENT,
                "Ulaanbaatar");
        draft.setBasePrice(new BigDecimal("100000"));
        propertyRepository.saveAndFlush(draft);

        mockMvc.perform(get("/api/v1/listings/{id}", draft.getId()))
                .andExpect(status().isNotFound());

        assertThatThrownBy(() -> bookingService.create(guest.getId(), draft.getId(),
                checkIn, checkOut, 2, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).getCode())
                .isEqualTo("listing_not_bookable");
    }

    @Test
    @DisplayName("suspending a host removes their listings from search immediately")
    void suspendingHostHidesListings() throws Exception {
        mockMvc.perform(get("/api/v1/listings/search").param("city", "Ulaanbaatar"))
                .andExpect(jsonPath("$.rows[?(@.id == '" + listing.getId() + "')]").exists());

        owner.setStatus(mn.innex.stay.user.domain.UserStatus.SUSPENDED);
        userRepository.saveAndFlush(owner);

        mockMvc.perform(get("/api/v1/listings/search").param("city", "Ulaanbaatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.id == '" + listing.getId() + "')]").doesNotExist());
        mockMvc.perform(get("/api/v1/listings/{id}", listing.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a guest cannot reach the owner API, and an owner cannot reach the admin API")
    void rolesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/owner/properties").with(as(guest, "ROLE_CLIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/properties").with(as(owner, "ROLE_HOUSE_OWNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/owner/properties").with(as(owner, "ROLE_HOUSE_OWNER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("one owner cannot see or edit another owner's listing")
    void listingsAreIsolatedBetweenOwners() throws Exception {
        User otherOwner = createUser(Role.HOUSE_OWNER);

        // Reported as missing, not forbidden: existence is not this caller's business.
        mockMvc.perform(get("/api/v1/owner/properties/{id}", listing.getId())
                        .with(as(otherOwner, "ROLE_HOUSE_OWNER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("two guests racing for the same dates: exactly one wins")
    void concurrentBookingsCannotDoubleBook() throws Exception {
        // The point of the exclusion constraint. An availability check followed by
        // an insert has a window between them; under load two guests will land in
        // it, and only the database can arbitrate.
        int racers = 8;
        List<User> contenders = new ArrayList<>();
        for (int index = 0; index < racers; index++) {
            contenders.add(createUser(Role.CLIENT));
        }

        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Callable<String>> attempts = new ArrayList<>();
            for (User contender : contenders) {
                attempts.add(() -> {
                    try {
                        bookingService.create(contender.getId(), listing.getId(),
                                checkIn, checkOut, 2, null, null);
                        return "booked";
                    } catch (ApiException ex) {
                        return ex.getCode();
                    } catch (RuntimeException ex) {
                        return "unexpected:" + ex.getClass().getSimpleName();
                    }
                });
            }

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : pool.invokeAll(attempts)) {
                outcomes.add(future.get());
            }

            assertThat(outcomes).filteredOn("booked"::equals)
                    .as("exactly one of %d concurrent attempts may succeed: %s", racers, outcomes)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> outcome.startsWith("unexpected"))
                    .as("losers must get a clean conflict, not a leaked exception: %s", outcomes)
                    .isEmpty();
            assertThat(outcomes).filteredOn("dates_unavailable"::equals)
                    .hasSize(racers - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(bookingRepository.hasOverlap(listing.getId(), checkIn, checkOut,
                BookingStatus.occupyingStates(), null)).isTrue();
    }

    @Test
    @DisplayName("a stay ending the day another begins is allowed")
    void adjacentStaysAreAllowed() {
        bookingService.create(guest.getId(), listing.getId(), checkIn, checkOut, 2, null, null);

        User nextGuest = createUser(Role.CLIENT);
        // Check-out is exclusive, so the same date is a valid check-in for the next
        // guest. Getting this wrong loses a night's revenue on every turnover.
        var back_to_back = bookingService.create(nextGuest.getId(), listing.getId(),
                checkOut, checkOut.plusDays(2), 2, null, null);

        assertThat(back_to_back.getCheckIn()).isEqualTo(checkOut);
    }

    @Test
    @DisplayName("a cancelled booking frees its dates")
    void cancellingFreesTheDates() {
        var booking = bookingService.create(guest.getId(), listing.getId(), checkIn, checkOut,
                2, null, null);
        bookingService.cancelByGuest(guest.getId(), booking.getId(), "changed plans", null);

        User nextGuest = createUser(Role.CLIENT);
        var rebooked = bookingService.create(nextGuest.getId(), listing.getId(), checkIn,
                checkOut, 2, null, null);

        assertThat(rebooked.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
    }

    @Test
    @DisplayName("stay rules are enforced: minimum nights, capacity and past dates")
    void stayRulesAreEnforced() throws Exception {
        mockMvc.perform(post("/api/v1/listings/{id}/quote", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(checkIn, checkIn.plusDays(1), 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("min_stay_not_met"));

        mockMvc.perform(post("/api/v1/listings/{id}/quote", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(checkIn, checkOut, 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("too_many_guests"));

        mockMvc.perform(post("/api/v1/listings/{id}/quote", listing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(LocalDate.now().minusDays(2), checkOut, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("check_in_in_past"));
    }

    @Test
    @DisplayName("an owner cannot book their own listing")
    void ownerCannotBookOwnListing() {
        assertThatThrownBy(() -> bookingService.create(owner.getId(), listing.getId(),
                checkIn, checkOut, 2, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).getCode())
                .isEqualTo("cannot_book_own_listing");
    }

    @Test
    @DisplayName("a payment callback with an invalid signature is refused")
    void callbackSignatureIsRequired() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callbacks/simulated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-simulated-signature", "not-a-real-signature")
                        .content("{\"invoiceId\":\"SIM-forged\",\"status\":\"PAID\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("callback_signature_invalid"));
    }

    // --- fixtures -----------------------------------------------------------

    private RequestPostProcessor as(User user, String... authorities) {
        // CurrentActor reads the subject, and the URL rules read the authorities.
        return jwt()
                .jwt(builder -> builder.subject(user.getId().toString()))
                .authorities(java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }

    private User createUser(Role role) {
        User user = User.createWithPhone("+9769" + PHONE_SEQUENCE.incrementAndGet(), "mn");
        user.markPhoneVerified();
        user.setFullName("Test Person");
        user.grantRole(Role.CLIENT, null, null);
        if (role != Role.CLIENT) {
            user.grantRole(role, null, null);
        }
        return userRepository.saveAndFlush(user);
    }

    private Property createApprovedListing(User listingOwner) {
        Property property = new Property(listingOwner, "Ger camp by the river", PropertyType.GER,
                "Ulaanbaatar");
        property.setDescription("A traditional ger with a wood stove.");
        property.setAddressLine("Gachuurt valley, plot 14");
        property.setDistrict("Bayanzurkh");
        property.setLocation(47.9405, 107.1210);
        property.setMaxGuests(4);
        property.setBasePrice(new BigDecimal("180000"));
        property.setCleaningFee(new BigDecimal("25000"));
        property.setMinStayNights(2);
        property.setCancellationPolicy(CancellationPolicy.MODERATE);
        // A CHECK constraint requires a photo-worthy, locatable listing before
        // APPROVED, so the fixture has to be complete.
        // Storage keys are globally unique, so each fixture needs its own.
        property.addPhoto(new PropertyPhoto(property,
                "properties/fixture/" + UUID.randomUUID() + ".jpg",
                "image/jpeg", 2048, "Cover", 0));
        property.approve();
        return propertyRepository.saveAndFlush(property);
    }

    private String quoteBody(LocalDate from, LocalDate to, int guests) {
        return """
                {"checkIn":"%s","checkOut":"%s","guests":%d}""".formatted(from, to, guests);
    }

    private String bookingBody(UUID propertyId, LocalDate from, LocalDate to, int guests) {
        return """
                {"propertyId":"%s","checkIn":"%s","checkOut":"%s","guests":%d}"""
                .formatted(propertyId, from, to, guests);
    }
}

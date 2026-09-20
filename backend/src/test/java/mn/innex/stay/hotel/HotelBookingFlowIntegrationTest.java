package mn.innex.stay.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.HotelPhoto;
import mn.innex.stay.hotel.domain.RoomInventoryDay;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.hotel.repo.RoomInventoryDayRepository;
import mn.innex.stay.hotel.repo.RoomTypeRepository;
import mn.innex.stay.user.domain.Organization;
import mn.innex.stay.user.domain.OrganizationStatus;
import mn.innex.stay.user.domain.OrganizationType;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.OrganizationRepository;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The hotel booking guarantees, especially the ones only concurrency reveals.
 *
 * <p>A house is exclusive and protected by an exclusion constraint. A room type is
 * <em>counted</em>, which no constraint can express directly — so the protection
 * is a database-maintained counter plus a CHECK, and these tests are what prove it
 * actually holds.
 */
class HotelBookingFlowIntegrationTest extends IntegrationTest {

    /** Distinguishes fixture names within this class; phones come from the base. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomInventoryDayRepository inventoryRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private mn.innex.stay.booking.service.HotelPerformanceService performanceService;

    @Autowired
    private mn.innex.stay.hotel.service.InventoryService inventoryService;

    private RoomType standardDouble;
    private UUID hotelId;
    private UUID managerId;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        User owner = createUser(Role.HOTEL_MANAGER);
        Organization organization = organizationRepository.saveAndFlush(new Organization(
                "Test Hotels " + SEQUENCE.incrementAndGet(), OrganizationType.HOTEL_BUSINESS,
                "REG" + SEQUENCE.incrementAndGet(), owner));
        organization.setStatus(OrganizationStatus.ACTIVE);
        organizationRepository.saveAndFlush(organization);
        // Reports read authority from the database, so the grant has to be real and
        // scoped to the organization that owns the hotel.
        owner.grantRole(Role.HOTEL_MANAGER, organization, null);
        managerId = userRepository.saveAndFlush(owner).getId();

        Hotel hotel = new Hotel(organization, "Test Hotel", "Ulaanbaatar");
        hotel.setDescription("A hotel for tests.");
        hotel.setAddressLine("Peace Avenue 1");
        hotel.setLocation(47.9188, 106.9176);
        hotel.addPhoto(new HotelPhoto(hotel, "hotels/fixture/" + java.util.UUID.randomUUID() + ".jpg",
                "image/jpeg", 2048, "Cover", 0));
        hotelRepository.saveAndFlush(hotel);
        hotelId = hotel.getId();

        standardDouble = new RoomType(hotel, "Standard double", 2, 3, new BigDecimal("220000"));
        roomTypeRepository.saveAndFlush(standardDouble);
        hotel.addRoomType(standardDouble);
        hotel.approve();
        hotelRepository.saveAndFlush(hotel);

        checkIn = PlatformTime.today().plusDays(50);
        checkOut = checkIn.plusDays(2);
    }

    @Test
    @DisplayName("twelve guests racing for three rooms: exactly three win")
    void concurrentBookingsCannotOversell() throws Exception {
        // The whole reason hotel inventory is a counted CHECK rather than an
        // availability query: under load, several guests read "rooms left" at the
        // same instant, and only the database can arbitrate who actually gets one.
        int racers = 12;
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
                        bookingService.createHotelBooking(contender.getId(),
                                standardDouble.getId(), checkIn, checkOut, 2, 1, null, null);
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
                    .as("a room type with 3 rooms may sell exactly 3: %s", outcomes)
                    .hasSize(3);
            assertThat(outcomes).filteredOn(outcome -> outcome.startsWith("unexpected"))
                    .as("losers must get a clean conflict, not a leaked exception: %s", outcomes)
                    .isEmpty();
            assertThat(outcomes).filteredOn("rooms_unavailable"::equals).hasSize(racers - 3);
        } finally {
            pool.shutdownNow();
        }

        // And the counter the database maintains agrees.
        List<RoomInventoryDay> nights = inventoryRepository.findInRange(
                standardDouble.getId(), checkIn, checkOut);
        assertThat(nights).hasSize(2);
        assertThat(nights).allSatisfy(night -> {
            assertThat(night.getBookedCount()).isEqualTo(3);
            assertThat(night.getAvailableCount()).isEqualTo(3);
            assertThat(night.remainingCount()).isZero();
        });
    }

    @Test
    @DisplayName("a multi-room reservation consumes that many rooms per night")
    void multiRoomBookingConsumesInventory() {
        User guest = createUser(Role.CLIENT);
        bookingService.createHotelBooking(guest.getId(), standardDouble.getId(),
                checkIn, checkOut, 4, 2, null, null);

        List<RoomInventoryDay> nights = inventoryRepository.findInRange(
                standardDouble.getId(), checkIn, checkOut);
        assertThat(nights).allSatisfy(night -> assertThat(night.getBookedCount()).isEqualTo(2));

        // One room left, so a two-room request must fail and a one-room succeed.
        User another = createUser(Role.CLIENT);
        assertThatThrownBy(() -> bookingService.createHotelBooking(another.getId(),
                standardDouble.getId(), checkIn, checkOut, 4, 2, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).getCode())
                .isEqualTo("rooms_unavailable");

        var lastRoom = bookingService.createHotelBooking(another.getId(),
                standardDouble.getId(), checkIn, checkOut, 2, 1, null, null);
        assertThat(lastRoom.getRoomCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelling a reservation returns its rooms to inventory")
    void cancellingReleasesRooms() {
        User guest = createUser(Role.CLIENT);
        var booking = bookingService.createHotelBooking(guest.getId(), standardDouble.getId(),
                checkIn, checkOut, 4, 2, null, null);

        bookingService.cancelByGuest(guest.getId(), booking.getId(), "changed plans", null);

        List<RoomInventoryDay> nights = inventoryRepository.findInRange(
                standardDouble.getId(), checkIn, checkOut);
        assertThat(nights).allSatisfy(night -> assertThat(night.getBookedCount()).isZero());

        // Which means the whole room type is sellable again.
        User another = createUser(Role.CLIENT);
        var rebooked = bookingService.createHotelBooking(another.getId(), standardDouble.getId(),
                checkIn, checkOut, 6, 3, null, null);
        assertThat(rebooked.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("a stay ending the day another begins does not consume the same night")
    void adjacentStaysShareNoNight() {
        User first = createUser(Role.CLIENT);
        bookingService.createHotelBooking(first.getId(), standardDouble.getId(),
                checkIn, checkOut, 6, 3, null, null);

        // Fully booked for its own nights, but check-out day is free again.
        User second = createUser(Role.CLIENT);
        var backToBack = bookingService.createHotelBooking(second.getId(), standardDouble.getId(),
                checkOut, checkOut.plusDays(2), 6, 3, null, null);

        assertThat(backToBack.getCheckIn()).isEqualTo(checkOut);
        assertThat(inventoryRepository.findByRoomTypeIdAndDay(standardDouble.getId(), checkOut))
                .get()
                .extracting(RoomInventoryDay::getBookedCount)
                .isEqualTo(3);
    }

    @Test
    @DisplayName("availability cannot be cut below the rooms already sold")
    void cannotCloseSoldRooms() {
        User guest = createUser(Role.CLIENT);
        bookingService.createHotelBooking(guest.getId(), standardDouble.getId(),
                checkIn, checkOut, 4, 2, null, null);

        RoomInventoryDay night = inventoryRepository
                .findByRoomTypeIdAndDay(standardDouble.getId(), checkIn).orElseThrow();
        night.setAvailableCount(1);

        // The database refuses: two rooms are sold, so one cannot be all there is.
        assertThatThrownBy(() -> {
            inventoryRepository.save(night);
            inventoryRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a guest cannot book more rooms than the hotel has")
    void cannotBookMoreRoomsThanExist() {
        User guest = createUser(Role.CLIENT);
        assertThatThrownBy(() -> bookingService.createHotelBooking(guest.getId(),
                standardDouble.getId(), checkIn, checkOut, 8, 4, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).getCode())
                .isEqualTo("too_many_rooms");
    }

    @Test
    @DisplayName("guests are checked against the capacity of the rooms booked")
    void capacityScalesWithRoomCount() {
        User guest = createUser(Role.CLIENT);
        // One double sleeps two, so three guests need a second room.
        assertThatThrownBy(() -> bookingService.createHotelBooking(guest.getId(),
                standardDouble.getId(), checkIn, checkOut, 3, 1, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).getCode())
                .isEqualTo("too_many_guests");

        var booked = bookingService.createHotelBooking(guest.getId(), standardDouble.getId(),
                checkIn, checkOut, 3, 2, null, null);
        assertThat(booked.getGuestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("an inactive room type cannot be booked, but its stays survive")
    void deactivatingARoomTypeKeepsExistingStays() {
        User guest = createUser(Role.CLIENT);
        var existing = bookingService.createHotelBooking(guest.getId(), standardDouble.getId(),
                checkIn, checkOut, 2, 1, null, null);

        standardDouble.setStatus(mn.innex.stay.hotel.domain.RoomTypeStatus.INACTIVE);
        roomTypeRepository.saveAndFlush(standardDouble);

        User another = createUser(Role.CLIENT);
        assertThatThrownBy(() -> bookingService.createHotelBooking(another.getId(),
                standardDouble.getId(), checkIn, checkOut, 2, 1, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).getCode())
                .isEqualTo("room_type_not_bookable");

        // Taking a room out of sale must not cancel someone's stay.
        assertThat(bookingService.requireForGuest(guest.getId(), existing.getId()).getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("occupancy counts the nights nobody has touched, not just the stored rows")
    void occupancyCountsUntouchedNights() {
        // No edits and no bookings in this window, so it has no inventory rows at
        // all. Capacity still has to be the rooms the hotel sells, or a hotel with
        // one booked night looks fully sold for the month.
        LocalDate from = checkIn.plusDays(100);
        LocalDate to = from.plusDays(1);

        var report = performanceService.report(managerId, hotelId, from, to);

        assertThat(report.roomNightsAvailable()).isEqualTo(6);
        assertThat(report.roomNightsSold()).isZero();
        assertThat(report.occupancyPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("one room sold out of three is a third of the occupancy, not all of it")
    void occupancyReflectsTheWholeHotel() {
        User guest = createUser(Role.CLIENT);
        bookingService.createHotelBooking(guest.getId(), standardDouble.getId(),
                checkIn, checkOut, 2, 1, null, null);

        // The stay covers two nights, and `to` is inclusive.
        var report = performanceService.report(managerId, hotelId, checkIn, checkOut.minusDays(1));

        assertThat(report.roomNightsAvailable()).isEqualTo(6);
        assertThat(report.roomNightsSold()).isEqualTo(2);
        assertThat(report.occupancyPercent()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("closing rooms for a night lowers the capacity it contributes")
    void closingRoomsLowersCapacity() {
        LocalDate from = checkIn.plusDays(120);
        inventoryService.updateRange(managerId, standardDouble.getId(),
                new mn.innex.stay.hotel.web.dto.HotelRequests.UpdateInventory(
                        from, from, List.of(), 1, null, null, null, null, null),
                "127.0.0.1");

        var report = performanceService.report(managerId, hotelId, from, from.plusDays(1));

        // One night offers one room instead of three; the next is untouched at three.
        assertThat(report.roomNightsAvailable()).isEqualTo(4);
    }

    private User createUser(Role role) {
        User user = User.createWithPhone(uniquePhone(), "mn");
        user.markPhoneVerified();
        user.setFullName("Test Person");
        user.grantRole(Role.CLIENT, null, null);
        if (role == Role.HOTEL_MANAGER || role == Role.HOUSE_OWNER) {
            // An org-scoped grant needs the organization, which the fixture creates
            // afterwards; the platform-wide grant is enough for these tests.
            user.grantRole(Role.HOUSE_OWNER, null, null);
        }
        return userRepository.saveAndFlush(user);
    }
}

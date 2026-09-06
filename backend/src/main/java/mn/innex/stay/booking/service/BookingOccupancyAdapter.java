package mn.innex.stay.booking.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.listing.service.OccupancyPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies booked dates to the listing module through the port it declares.
 *
 * <p>This is the booking side of that seam: the only place the listing module's
 * view of occupancy is defined, and the thing that would become an HTTP or
 * messaging client if booking were ever split into its own service.
 */
@Component
public class BookingOccupancyAdapter implements OccupancyPort {

    private final BookingRepository bookingRepository;

    public BookingOccupancyAdapter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public List<String> occupyingStatusNames() {
        return BookingStatus.occupyingStates().stream().map(BookingStatus::name).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedRange> occupiedRanges(UUID propertyId, LocalDate from, LocalDate toExclusive) {
        return bookingRepository
                .findOccupiedRanges(propertyId, from, toExclusive, BookingStatus.occupyingStates())
                .stream()
                .map(row -> new OccupiedRange((LocalDate) row[0], (LocalDate) row[1]))
                .toList();
    }
}

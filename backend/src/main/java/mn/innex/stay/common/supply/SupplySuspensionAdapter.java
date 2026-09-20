package mn.innex.stay.common.supply;

import java.util.UUID;

import mn.innex.stay.hotel.repo.RoomTypeRepository;
import mn.innex.stay.hotel.service.HotelService;
import mn.innex.stay.listing.service.PropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes a suspension to whichever module owns the listing.
 *
 * <p>Lives beside the port rather than in either module, because it is the one
 * place that has to know about both. A room type suspends its whole hotel: a
 * stolen photograph on one room says nothing good about the rest of it, and the
 * alternative is a hotel that is half on sale.
 */
@Component
public class SupplySuspensionAdapter implements SupplySuspensionPort {

    private static final Logger log = LoggerFactory.getLogger(SupplySuspensionAdapter.class);

    private final PropertyService properties;
    private final HotelService hotels;
    private final RoomTypeRepository roomTypes;

    public SupplySuspensionAdapter(PropertyService properties, HotelService hotels,
                                   RoomTypeRepository roomTypes) {
        this.properties = properties;
        this.hotels = hotels;
        this.roomTypes = roomTypes;
    }

    @Override
    public void suspend(SupplyKind kind, UUID supplyId, UUID actorId, String reason, String ip) {
        try {
            switch (kind) {
                case PROPERTY -> properties.setStatusAsAdmin(actorId, supplyId,
                        SupplyStatus.SUSPENDED, reason, ip);
                case HOTEL -> hotels.setStatusAsAdmin(actorId, supplyId,
                        SupplyStatus.SUSPENDED, reason, ip);
                case ROOM_TYPE -> roomTypes.findById(supplyId).ifPresentOrElse(
                        roomType -> hotels.setStatusAsAdmin(actorId,
                                roomType.getHotel().getId(), SupplyStatus.SUSPENDED, reason, ip),
                        () -> log.warn("Flagged room type {} no longer exists", supplyId));
            }
        } catch (RuntimeException ex) {
            // The flag decision itself must stand even if the listing cannot be
            // taken down — the money stays frozen either way.
            log.error("Could not suspend {} {}", kind, supplyId, ex);
        }
    }
}

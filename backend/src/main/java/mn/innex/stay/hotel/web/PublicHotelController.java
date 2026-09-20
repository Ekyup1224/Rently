package mn.innex.stay.hotel.web;

import java.util.UUID;

import mn.innex.stay.hotel.service.HotelPhotoService;
import mn.innex.stay.hotel.service.HotelService;
import mn.innex.stay.hotel.service.RoomTypeService;
import mn.innex.stay.hotel.web.dto.HotelResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The guest-facing hotel page.
 *
 * <p>Unauthenticated, like the house catalogue: browsing and comparing need no
 * account. Only approved hotels belonging to an active business resolve, so
 * suspending either removes the hotel from the catalogue immediately.
 *
 * <p>Availability and pricing for specific dates live in the booking module,
 * because they are pricing questions — this module has no dependency on booking.
 */
@RestController
@RequestMapping("/api/v1/hotels")
public class PublicHotelController {

    private final HotelService hotelService;
    private final RoomTypeService roomTypeService;
    private final HotelPhotoService photoService;

    public PublicHotelController(HotelService hotelService, RoomTypeService roomTypeService,
                                 HotelPhotoService photoService) {
        this.hotelService = hotelService;
        this.roomTypeService = roomTypeService;
        this.photoService = photoService;
    }

    /** The hotel and its sellable room types, without date-specific pricing. */
    @GetMapping("/{hotelId}")
    public HotelResponses.PublicHotel detail(@PathVariable UUID hotelId) {
        return HotelResponses.PublicHotel.from(
                hotelService.requirePubliclyVisible(hotelId),
                roomTypeService.listSellable(hotelId),
                photoService);
    }
}

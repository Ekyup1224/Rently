package mn.innex.stay.booking.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.booking.service.HotelPricingService;
import mn.innex.stay.booking.service.Quote;
import mn.innex.stay.booking.web.dto.QuoteResponse;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.service.HotelPhotoService;
import mn.innex.stay.hotel.service.HotelService;
import mn.innex.stay.hotel.service.RoomTypeService;
import mn.innex.stay.hotel.web.dto.HotelResponses;
import mn.innex.stay.listing.web.dto.QuoteRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a hotel's rooms cost and whether they are free.
 *
 * <p>Lives in the booking module rather than the hotel module because these are
 * pricing questions, and pricing belongs next to booking so that a quote and the
 * booking made from it cannot disagree. The hotel module deliberately knows
 * nothing about bookings.
 *
 * <p>Unauthenticated: a guest compares rooms and totals before signing in.
 */
@RestController
@RequestMapping("/api/v1/hotels/{hotelId}")
public class HotelAvailabilityController {

    private final HotelService hotelService;
    private final RoomTypeService roomTypeService;
    private final HotelPricingService pricingService;
    private final BookingService bookingService;
    private final HotelPhotoService photoService;

    public HotelAvailabilityController(HotelService hotelService, RoomTypeService roomTypeService,
                                       HotelPricingService pricingService,
                                       BookingService bookingService,
                                       HotelPhotoService photoService) {
        this.hotelService = hotelService;
        this.roomTypeService = roomTypeService;
        this.pricingService = pricingService;
        this.bookingService = bookingService;
        this.photoService = photoService;
    }

    /**
     * Every room type with whether it can take these dates, how many are left, and
     * what the stay would cost.
     *
     * <p>One call rather than a quote per room type, because that is what the room
     * list on a hotel page needs. A room type that cannot take the stay is still
     * returned, with the reason, so the page can explain itself rather than
     * silently hiding options.
     */
    @GetMapping("/availability")
    public List<HotelResponses.RoomTypeAvailability> availability(
            @PathVariable UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "2") int guests,
            @RequestParam(required = false, defaultValue = "1") int rooms) {
        // Resolving through the public path keeps an unapproved hotel invisible here too.
        var hotel = hotelService.requirePubliclyVisible(hotelId);
        if (!checkOut.isAfter(checkIn)) {
            throw ApiException.badRequest("invalid_date_range", "checkOut must be after checkIn");
        }

        List<HotelResponses.RoomTypeAvailability> availability = new ArrayList<>();
        for (RoomType roomType : roomTypeService.listSellable(hotelId)) {
            int roomsLeft = pricingService.roomsLeftForStay(roomType, checkIn, checkOut);
            List<HotelResponses.Photo> photos = roomType.getPhotos().stream()
                    .map(photo -> new HotelResponses.Photo(photo.getId(),
                            photoService.publicUrl(photo.getStorageKey()), photo.getAltText(),
                            photo.getSortOrder(), photo.isCover()))
                    .toList();

            try {
                Quote quote = pricingService.quote(roomType, checkIn, checkOut, guests, rooms);
                availability.add(new HotelResponses.RoomTypeAvailability(
                        roomType.getId(), roomType.getName(), roomType.getCapacity(), true, null,
                        roomsLeft, roomType.getBasePrice(), quote.total(), quote.currency(),
                        photos));
            } catch (ApiException refused) {
                // Pricing is also the validator, so its message is the honest reason
                // this room cannot take the stay.
                availability.add(new HotelResponses.RoomTypeAvailability(
                        roomType.getId(), roomType.getName(), roomType.getCapacity(), false,
                        refused.getMessage(), roomsLeft, roomType.getBasePrice(), null,
                        hotel.getCurrency(), photos));
            }
        }
        return availability;
    }

    /**
     * Prices one room type for a stay, itemized.
     *
     * @param rooms rooms of this type; defaults to one
     */
    @PostMapping("/room-types/{roomTypeId}/quote")
    public QuoteResponse quote(@PathVariable UUID hotelId, @PathVariable UUID roomTypeId,
                               @Valid @RequestBody QuoteRequest request,
                               @RequestParam(required = false, defaultValue = "1") int rooms) {
        hotelService.requirePubliclyVisible(hotelId);
        return QuoteResponse.from(bookingService.quoteHotelStay(
                roomTypeId, request.checkIn(), request.checkOut(), request.guests(), rooms));
    }
}

package mn.innex.stay.admin.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.hotel.service.HotelPhotoService;
import mn.innex.stay.hotel.service.HotelService;
import mn.innex.stay.hotel.web.dto.HotelRequests;
import mn.innex.stay.hotel.web.dto.HotelResponses;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hotel review, mirroring the listing queue.
 *
 * <p>A hotel additionally cannot be approved without a sellable room type, since
 * the hotel itself is not what a guest books.
 */
@RestController
@RequestMapping("/api/v1/admin/hotels")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminHotelController {

    private final HotelService hotelService;
    private final HotelPhotoService photoService;

    public AdminHotelController(HotelService hotelService, HotelPhotoService photoService) {
        this.hotelService = hotelService;
        this.photoService = photoService;
    }

    /** @param status pass {@code PENDING_REVIEW} for the queue itself */
    @GetMapping
    public PageResponse<HotelResponses.ManagedHotel> list(
            @RequestParam(required = false) SupplyStatus status,
            @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(hotelService.listByStatusForAdmin(status, pageable),
                hotel -> HotelResponses.ManagedHotel.from(hotel, photoService));
    }

    @GetMapping("/{hotelId}")
    public HotelResponses.ManagedHotel get(@PathVariable UUID hotelId) {
        return HotelResponses.ManagedHotel.from(hotelService.requireForAdmin(hotelId), photoService);
    }

    /** Approves, rejects or suspends. Rejection and suspension require a reason. */
    @PatchMapping("/{hotelId}/status")
    public HotelResponses.ManagedHotel setStatus(
            @PathVariable UUID hotelId,
            @Valid @RequestBody HotelRequests.HotelStatusDecision request,
            HttpServletRequest httpRequest) {
        return HotelResponses.ManagedHotel.from(hotelService.setStatusAsAdmin(
                CurrentActor.requireUserId(), hotelId, request.status(), request.reason(),
                ClientIp.of(httpRequest)), photoService);
    }
}

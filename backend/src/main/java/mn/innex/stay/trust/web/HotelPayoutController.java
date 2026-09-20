package mn.innex.stay.trust.web;

import java.util.UUID;

import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.hotel.service.HotelAccessService;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.trust.service.PayoutService;
import mn.innex.stay.trust.web.dto.TrustResponses;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A hotel business's payouts. Scoped to the organization rather than the person,
 * because that is who the money belongs to — and checked through
 * {@link HotelAccessService} so a staff account cannot read the books.
 */
@RestController
@RequestMapping("/api/v1/hotel")
public class HotelPayoutController {

    private final PayoutService payouts;
    private final HotelAccessService access;

    public HotelPayoutController(PayoutService payouts, HotelAccessService access) {
        this.payouts = payouts;
        this.access = access;
    }

    @GetMapping("/payouts")
    public PageResponse<TrustResponses.PayoutView> forHotel(
            @RequestParam UUID hotelId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        // Manager-level: money is not a front-desk concern.
        var hotel = access.requireManagerForChange(CurrentActor.requireUserId(), hotelId);
        return PageResponse.of(
                payouts.forOrganization(hotel.getOrganization().getId(), pageable),
                TrustResponses.PayoutView::from);
    }
}

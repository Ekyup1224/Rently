package mn.innex.stay.trust.web;

import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.trust.service.PayoutService;
import mn.innex.stay.trust.web.dto.TrustResponses;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a host is owed and when it arrives.
 *
 * <p>Worth showing plainly rather than hiding: a host who can see "held until the
 * day after your guest arrives" understands the rule, while one who just sees
 * nothing in their account assumes they have been cheated — and starts asking
 * guests to pay them directly, which is the outcome this whole module exists to
 * prevent.
 */
@RestController
@RequestMapping("/api/v1/owner")
public class HostPayoutController {

    private final PayoutService payouts;

    public HostPayoutController(PayoutService payouts) {
        this.payouts = payouts;
    }

    @GetMapping("/payouts")
    public PageResponse<TrustResponses.PayoutView> mine(
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(payouts.forPayee(CurrentActor.requireUserId(), pageable),
                TrustResponses.PayoutView::from);
    }
}

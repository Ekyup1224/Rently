package mn.innex.stay.trust.web;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.domain.PayoutStatus;
import mn.innex.stay.trust.service.KycService;
import mn.innex.stay.trust.service.ListingFlagService;
import mn.innex.stay.trust.service.PayoutService;
import mn.innex.stay.trust.web.dto.TrustRequests;
import mn.innex.stay.trust.web.dto.TrustResponses;
import mn.innex.stay.user.domain.KycStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trust and safety desk: money waiting to move, listings under suspicion,
 * and identity documents waiting to be checked.
 *
 * <p>Three queues in one place because they are one job. A flag raised on a
 * listing freezes its payouts, and an unverified host cannot be paid, so a
 * reviewer working any of these is really answering the same question: should
 * this money go out?
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTrustController {

    private final PayoutService payouts;
    private final ListingFlagService flags;
    private final KycService kyc;

    public AdminTrustController(PayoutService payouts, ListingFlagService flags, KycService kyc) {
        this.payouts = payouts;
        this.flags = flags;
        this.kyc = kyc;
    }

    // --- payouts ---------------------------------------------------------

    /**
     * @param status repeatable; omit for everything. {@code BLOCKED} is the queue
     *               that needs a person, {@code RELEASED} is what to pay next.
     */
    @GetMapping("/payouts")
    public PageResponse<TrustResponses.PayoutView> listPayouts(
            @RequestParam(required = false) List<PayoutStatus> status,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(payouts.list(status, pageable), TrustResponses.PayoutView::from);
    }

    /**
     * Runs the release sweep now instead of waiting for tonight.
     *
     * <p>Nothing here bypasses a rule — it applies exactly the same conditions the
     * scheduled job does. It exists because the usual reason to want it is having
     * just cleared a flag, and telling a host their money moves tomorrow when the
     * obstacle is already gone is a bad answer.
     *
     * @return how many payouts were released
     */
    @PostMapping("/payouts/release-due")
    public ReleaseSweepResult releaseDue() {
        return new ReleaseSweepResult(payouts.releaseDue());
    }

    /** @param released how many payouts moved to RELEASED */
    public record ReleaseSweepResult(int released) {
    }

    /** Overrides a hold. The admin's id goes on the record. */
    @PostMapping("/payouts/{payoutId}/release")
    public TrustResponses.PayoutView releasePayout(@PathVariable UUID payoutId,
                                                   @Valid @RequestBody TrustRequests.ReleasePayout request,
                                                   HttpServletRequest httpRequest) {
        return TrustResponses.PayoutView.from(payouts.releaseByHand(CurrentActor.requireUserId(),
                payoutId, request.note(), ClientIp.of(httpRequest)));
    }

    @PostMapping("/payouts/{payoutId}/hold")
    public TrustResponses.PayoutView holdPayout(@PathVariable UUID payoutId,
                                                @Valid @RequestBody TrustRequests.HoldPayout request,
                                                HttpServletRequest httpRequest) {
        return TrustResponses.PayoutView.from(payouts.hold(CurrentActor.requireUserId(), payoutId,
                request.reason(), ClientIp.of(httpRequest)));
    }

    /** Records the bank transfer once it has actually been sent. */
    @PostMapping("/payouts/{payoutId}/mark-paid")
    public TrustResponses.PayoutView markPaid(@PathVariable UUID payoutId,
                                              @Valid @RequestBody TrustRequests.MarkPayoutPaid request,
                                              HttpServletRequest httpRequest) {
        return TrustResponses.PayoutView.from(payouts.markPaid(CurrentActor.requireUserId(),
                payoutId, request.providerRef(), request.note(), ClientIp.of(httpRequest)));
    }

    // --- flags -----------------------------------------------------------

    @GetMapping("/flags")
    public PageResponse<TrustResponses.FlagView> listFlags(
            @RequestParam(required = false) List<FlagStatus> status,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(flags.list(status, pageable), TrustResponses.FlagView::from);
    }

    /** Nothing wrong here. Frees the listing's payouts if this was the last flag. */
    @PostMapping("/flags/{flagId}/dismiss")
    public TrustResponses.FlagView dismissFlag(@PathVariable UUID flagId,
                                               @Valid @RequestBody TrustRequests.ResolveFlag request,
                                               HttpServletRequest httpRequest) {
        return TrustResponses.FlagView.from(flags.resolve(CurrentActor.requireUserId(), flagId,
                FlagStatus.DISMISSED, request.note(), ClientIp.of(httpRequest)));
    }

    /** Confirmed: the listing comes off sale and its money stays frozen. */
    @PostMapping("/flags/{flagId}/uphold")
    public TrustResponses.FlagView upholdFlag(@PathVariable UUID flagId,
                                              @Valid @RequestBody TrustRequests.ResolveFlag request,
                                              HttpServletRequest httpRequest) {
        return TrustResponses.FlagView.from(flags.resolve(CurrentActor.requireUserId(), flagId,
                FlagStatus.UPHELD, request.note(), ClientIp.of(httpRequest)));
    }

    // --- identity --------------------------------------------------------

    @GetMapping("/kyc")
    public PageResponse<TrustResponses.KycView> listKyc(
            @RequestParam(required = false) List<KycStatus> status,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(kyc.queue(status, pageable), TrustResponses.KycView::forReviewer);
    }

    /** Verifying here is what unblocks that host's held payouts at the next sweep. */
    @PostMapping("/kyc/{submissionId}/review")
    public TrustResponses.KycView reviewKyc(@PathVariable UUID submissionId,
                                            @Valid @RequestBody TrustRequests.ReviewKyc request,
                                            HttpServletRequest httpRequest) {
        return TrustResponses.KycView.forReviewer(kyc.review(CurrentActor.requireUserId(),
                submissionId, request.outcome(), request.note(), ClientIp.of(httpRequest)));
    }
}

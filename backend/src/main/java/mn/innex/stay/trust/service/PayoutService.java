package mn.innex.stay.trust.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.domain.Payout;
import mn.innex.stay.trust.domain.PayoutStatus;
import mn.innex.stay.trust.repo.ListingFlagRepository;
import mn.innex.stay.trust.repo.PayoutRepository;
import mn.innex.stay.user.domain.KycStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds host money until the stay is real, then lets it go.
 *
 * <p>This is the part that actually defeats a fake listing. Approval queues and
 * photo checks can be fooled by someone who tries hard enough; a guest standing
 * where the house was supposed to be cannot. So the money waits until after
 * check-in, and anything that casts doubt in the meantime — an unverified payee,
 * a guest report, a photo belonging to someone else — stops it.
 *
 * <p>Blocking is always recoverable and releasing never is, which is why every
 * uncertain case blocks.
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);

    /** A stay the guest has actually started. Anything less is not evidence. */
    private static final List<BookingStatus> ARRIVED = List.of(
            BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT, BookingStatus.COMPLETED);

    private final PayoutRepository payouts;
    private final ListingFlagRepository flags;
    private final TrustProperties properties;
    private final AuditService auditService;

    public PayoutService(PayoutRepository payouts, ListingFlagRepository flags,
                         TrustProperties properties, AuditService auditService) {
        this.payouts = payouts;
        this.flags = flags;
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * Schedules what a confirmed booking will pay its host.
     *
     * <p>Called when payment settles. Idempotent: a replayed provider callback
     * must not create a second payout, which is also enforced by a unique index
     * on the booking.
     */
    @Transactional
    public Payout schedule(Booking booking) {
        return payouts.findByBookingId(booking.getId()).orElseGet(() -> {
            Instant releaseAfter = booking.getCheckIn()
                    .atStartOfDay(ZoneId.of("Asia/Ulaanbaatar"))
                    .toInstant()
                    .plus(properties.payoutHold());

            Payout payout = payouts.save(new Payout(booking, booking.getHost(),
                    booking.getOrganization(), booking.getHostPayout(), booking.getCurrency(),
                    releaseAfter));
            auditService.record(null, AuditAction.PAYOUT_SCHEDULED, "Payout", payout.getId(),
                    Map.of("booking", booking.getReference(),
                            "amount", booking.getHostPayout().toPlainString(),
                            "releaseAfter", releaseAfter.toString()), null);
            return payout;
        });
    }

    /** The stay is off; nothing is owed. Safe to call when no payout exists. */
    @Transactional
    public void cancelForBooking(UUID bookingId, String reason) {
        payouts.findByBookingId(bookingId).ifPresent(payout -> {
            if (payout.getStatus() == PayoutStatus.PAID) {
                // Already sent: recovering it is a manual, human problem.
                log.error("Booking {} was cancelled after its payout was paid", bookingId);
                return;
            }
            payout.cancel(reason);
            payouts.save(payout);
            auditService.record(null, AuditAction.PAYOUT_CANCELLED, "Payout", payout.getId(),
                    Map.of("reason", reason), null);
        });
    }

    /**
     * Runs the release rules over everything whose hold has expired.
     *
     * @return how many payouts were released
     */
    @Transactional
    public int releaseDue() {
        List<Payout> due = payouts.findDue(Instant.now());
        int released = 0;
        for (Payout payout : due) {
            String blocker = blockerFor(payout);
            if (blocker == null) {
                payout.release(null);
                released++;
                auditService.record(null, AuditAction.PAYOUT_RELEASED, "Payout", payout.getId(),
                        Map.of("amount", payout.getAmount().toPlainString(), "by", "SCHEDULE"),
                        null);
            } else {
                payout.block(blocker);
                auditService.record(null, AuditAction.PAYOUT_BLOCKED, "Payout", payout.getId(),
                        Map.of("reason", blocker), null);
            }
            payouts.save(payout);
        }
        if (!due.isEmpty()) {
            log.info("Payout sweep: released {} of {} due", released, due.size());
        }
        return released;
    }

    /**
     * Why this payout may not go out, or null when nothing stands in the way.
     *
     * <p>Read top to bottom, this is the entire trust policy.
     */
    private String blockerFor(Payout payout) {
        Booking booking = payout.getBooking();
        if (!ARRIVED.contains(booking.getStatus())) {
            // The clearest signal there is: nobody ever checked in. Either the
            // front desk was never worked, or the guest never found the place.
            return "stay_not_started";
        }
        if (payout.getPayee().getKycStatus() != KycStatus.VERIFIED) {
            return "kyc_required";
        }
        if (hasOpenFlag(booking)) {
            return "listing_flagged";
        }
        return null;
    }

    private boolean hasOpenFlag(Booking booking) {
        List<UUID> supplyIds = booking.isHotelStay()
                ? List.of(booking.getRoomType().getId(),
                          booking.getRoomType().getHotel().getId())
                : List.of(booking.getProperty().getId());
        return !flags.findBySupplyIdInAndStatus(supplyIds, FlagStatus.OPEN).isEmpty();
    }

    /**
     * Freezes everything still owed on a listing, the moment doubt appears.
     *
     * @return how many payouts were frozen
     */
    @Transactional
    public int freezeForSupply(UUID supplyId, String reason) {
        List<Payout> affected = payouts.findUnsettledForSupply(supplyId).stream()
                .filter(payout -> payout.getStatus() != PayoutStatus.BLOCKED)
                .toList();
        for (Payout payout : affected) {
            payout.block(reason);
            payouts.save(payout);
            auditService.record(null, AuditAction.PAYOUT_BLOCKED, "Payout", payout.getId(),
                    Map.of("reason", reason, "supplyId", supplyId.toString()), null);
        }
        return affected.size();
    }

    /**
     * Returns frozen payouts to waiting once the doubt is resolved. They still
     * have to pass the release rules afterwards — unfreezing is not approving.
     */
    @Transactional
    public int unfreezeForSupply(UUID supplyId) {
        List<Payout> affected = payouts.findUnsettledForSupply(supplyId).stream()
                .filter(payout -> payout.getStatus() == PayoutStatus.BLOCKED)
                .toList();
        affected.forEach(payout -> {
            payout.unblock();
            payouts.save(payout);
        });
        return affected.size();
    }

    @Transactional(readOnly = true)
    public Page<Payout> forPayee(UUID payeeId, Pageable pageable) {
        return payouts.findByPayeeId(payeeId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payout> forOrganization(UUID organizationId, Pageable pageable) {
        return payouts.findByOrganizationId(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payout> list(List<PayoutStatus> statuses, Pageable pageable) {
        return payouts.findByStatusIn(
                statuses == null || statuses.isEmpty() ? List.of(PayoutStatus.values())
                        : statuses, pageable);
    }

    /** An admin releasing something the rules held, with their name on it. */
    @Transactional
    public Payout releaseByHand(UUID adminId, UUID payoutId, String note, String ip) {
        Payout payout = require(payoutId);
        if (payout.getStatus().isFinal()) {
            throw ApiException.conflict("payout_settled", "That payout is already settled");
        }
        payout.release(adminId);
        payouts.save(payout);
        auditService.record(adminId, AuditAction.PAYOUT_RELEASED, "Payout", payoutId,
                Map.of("amount", payout.getAmount().toPlainString(), "by", "ADMIN",
                        "note", note == null ? "" : note), ip);
        return payout;
    }

    @Transactional
    public Payout hold(UUID adminId, UUID payoutId, String reason, String ip) {
        Payout payout = require(payoutId);
        if (payout.getStatus().isFinal()) {
            throw ApiException.conflict("payout_settled", "That payout is already settled");
        }
        payout.block(reason);
        payouts.save(payout);
        auditService.record(adminId, AuditAction.PAYOUT_BLOCKED, "Payout", payoutId,
                Map.of("reason", reason, "by", "ADMIN"), ip);
        return payout;
    }

    /** Records that the transfer actually happened, with its bank reference. */
    @Transactional
    public Payout markPaid(UUID adminId, UUID payoutId, String providerRef, String note,
                           String ip) {
        Payout payout = require(payoutId);
        if (payout.getStatus() != PayoutStatus.RELEASED) {
            throw ApiException.conflict("payout_not_released",
                    "Only a released payout can be marked paid");
        }
        payout.markPaid(providerRef, note);
        payouts.save(payout);
        auditService.record(adminId, AuditAction.PAYOUT_PAID, "Payout", payoutId,
                Map.of("amount", payout.getAmount().toPlainString(), "ref", providerRef), ip);
        return payout;
    }

    private Payout require(UUID payoutId) {
        return payouts.findByIdWithDetails(payoutId)
                .orElseThrow(() -> ApiException.notFound("payout_not_found", "No such payout"));
    }
}

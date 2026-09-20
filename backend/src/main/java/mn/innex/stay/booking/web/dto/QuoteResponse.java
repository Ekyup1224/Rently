package mn.innex.stay.booking.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.booking.service.Quote;
import mn.innex.stay.common.supply.CancellationPolicy;

/**
 * What a stay would cost, itemized. Shown before booking so the total is never a
 * surprise, and recomputed server-side at booking time from the same code.
 */
public record QuoteResponse(
        String supplyType,
        UUID supplyId,
        int rooms,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int guests,
        String currency,
        List<NightlyRateResponse> nightlyRates,
        BigDecimal nightlySubtotal,
        BigDecimal cleaningFee,
        BigDecimal guestServiceFee,
        BigDecimal tax,
        BigDecimal total,
        CancellationPolicy cancellationPolicy,
        List<RefundWindowResponse> refundSchedule,
        boolean instantBook) {

    public record NightlyRateResponse(LocalDate date, BigDecimal amount, boolean overridden) {
    }

    public record RefundWindowResponse(LocalDate cancelBefore, BigDecimal refundAmount,
                                       String description) {
    }

    /**
     * Host-side figures (commission and payout) are deliberately omitted: a guest
     * has no need to see what the platform takes from their host.
     */
    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(
                quote.supplyType().name(), quote.supplyId(), quote.rooms(),
                quote.checkIn(), quote.checkOut(), quote.nights(),
                quote.guests(), quote.currency(),
                quote.nightlyRates().stream()
                        .map(rate -> new NightlyRateResponse(rate.date(), rate.amount(),
                                rate.overridden()))
                        .toList(),
                quote.nightlySubtotal(), quote.cleaningFee(), quote.guestServiceFee(),
                quote.tax(), quote.total(), quote.cancellationPolicy(),
                quote.refundSchedule().stream()
                        .map(window -> new RefundWindowResponse(window.cancelBefore(),
                                window.refundAmount(), window.description()))
                        .toList(),
                quote.instantBook());
    }
}

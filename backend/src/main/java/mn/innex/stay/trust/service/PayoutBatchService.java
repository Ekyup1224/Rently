package mn.innex.stay.trust.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.trust.domain.Payout;
import mn.innex.stay.trust.domain.PayoutBatch;
import mn.innex.stay.trust.domain.PayoutBatchStatus;
import mn.innex.stay.trust.repo.PayoutBatchRepository;
import mn.innex.stay.trust.repo.PayoutRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turning released payouts into transfer runs.
 *
 * <p>Automation stops at the bank door, deliberately. No Mongolian bank offers
 * an API this can call, so what "automated" means here is: the sweep decides
 * what is owed, this groups it per host, and a person uploads one file. Pretending
 * to send money we cannot send would be worse than admitting the last step is
 * manual.
 */
@Service
public class PayoutBatchService {

    private final PayoutBatchRepository batches;
    private final PayoutRepository payouts;
    private final AuditService auditService;

    public PayoutBatchService(PayoutBatchRepository batches, PayoutRepository payouts,
                              AuditService auditService) {
        this.batches = batches;
        this.payouts = payouts;
        this.auditService = auditService;
    }

    /**
     * Collects everything released and unbatched into one run.
     *
     * @throws ApiException 409 when there is nothing waiting
     */
    @Transactional
    public PayoutBatch assemble(UUID adminId, String ip) {
        List<Payout> waiting = payouts.findReleasedWithoutBatch();
        if (waiting.isEmpty()) {
            throw ApiException.conflict("nothing_to_pay", "No released payouts are waiting");
        }

        BigDecimal total = waiting.stream()
                .map(Payout::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayoutBatch batch = batches.save(new PayoutBatch(adminId, waiting.size(),
                Money.of(total), waiting.getFirst().getCurrency()));
        waiting.forEach(payout -> {
            payout.assignTo(batch);
            payouts.save(payout);
        });

        auditService.record(adminId, AuditAction.PAYOUT_BATCH_CREATED, "PayoutBatch",
                batch.getId(),
                Map.of("count", String.valueOf(waiting.size()), "total", total.toPlainString()),
                ip);
        return batch;
    }

    @Transactional(readOnly = true)
    public Page<PayoutBatch> list(Pageable pageable) {
        return batches.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * One line per host, which is the whole point of batching: eleven stays
     * become one transfer and one line on a bank statement.
     *
     * @return rows of payee, amount and the stays that made it up
     */
    @Transactional(readOnly = true)
    public List<TransferLine> transferLines(UUID batchId) {
        List<Payout> members = payouts.findByBatchId(batchId);
        if (members.isEmpty()) {
            throw ApiException.notFound("batch_not_found", "No such batch, or it is empty");
        }

        Map<UUID, TransferLine> byPayee = new LinkedHashMap<>();
        for (Payout payout : members) {
            byPayee.merge(payout.getPayee().getId(),
                    new TransferLine(payout.getPayee().getId(),
                            payout.getPayee().getFullName(),
                            payout.getPayee().getPhone(),
                            payout.getOrganization() == null
                                    ? null : payout.getOrganization().getName(),
                            payout.getAmount(), payout.getCurrency(),
                            List.of(payout.getBooking().getReference())),
                    TransferLine::plus);
        }
        return List.copyOf(byPayee.values());
    }

    /** Records that the file has gone to the bank. */
    @Transactional
    public PayoutBatch markExported(UUID adminId, UUID batchId, String ip) {
        PayoutBatch batch = batches.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("batch_not_found", "No such batch"));
        if (batch.getStatus() != PayoutBatchStatus.OPEN) {
            throw ApiException.conflict("batch_already_exported",
                    "That batch has already been exported");
        }
        batch.markExported();
        batches.save(batch);
        auditService.record(adminId, AuditAction.PAYOUT_BATCH_EXPORTED, "PayoutBatch", batchId,
                Map.of("count", String.valueOf(batch.getPayoutCount())), ip);
        return batch;
    }

    /**
     * Confirms the bank actually moved the money, and marks every payout in the
     * run as paid against the same reference.
     */
    @Transactional
    public PayoutBatch settle(UUID adminId, UUID batchId, String providerRef, String ip) {
        PayoutBatch batch = batches.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("batch_not_found", "No such batch"));
        if (batch.getStatus() == PayoutBatchStatus.SETTLED) {
            throw ApiException.conflict("batch_already_settled", "That batch is already settled");
        }

        payouts.findByBatchId(batchId).forEach(payout -> {
            payout.markPaid(providerRef, "Batch " + batchId);
            payouts.save(payout);
        });
        batch.markSettled();
        batches.save(batch);

        auditService.record(adminId, AuditAction.PAYOUT_PAID, "PayoutBatch", batchId,
                Map.of("ref", providerRef, "count", String.valueOf(batch.getPayoutCount())), ip);
        return batch;
    }

    /**
     * What one host is owed in a run.
     *
     * @param bookings the stays behind the figure, so a host querying the amount
     *                 can be answered without a database lookup
     */
    public record TransferLine(UUID payeeId, String payeeName, String payeePhone,
                               String organizationName, BigDecimal amount, String currency,
                               List<String> bookings) {

        TransferLine plus(TransferLine other) {
            return new TransferLine(payeeId, payeeName, payeePhone, organizationName,
                    amount.add(other.amount), currency,
                    java.util.stream.Stream.concat(bookings.stream(), other.bookings.stream())
                            .toList());
        }
    }
}

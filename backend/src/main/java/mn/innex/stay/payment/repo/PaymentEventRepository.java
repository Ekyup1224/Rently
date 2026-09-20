package mn.innex.stay.payment.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.payment.domain.PaymentEvent;
import mn.innex.stay.payment.domain.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    /** The provider's side of one payment's story, oldest first. */
    java.util.List<PaymentEvent> findByPaymentIdOrderByReceivedAtAsc(UUID paymentId);

    /** Dedupe check for a redelivered callback. */
    Optional<PaymentEvent> findByProviderAndProviderEventId(PaymentProvider provider,
                                                            String providerEventId);
}

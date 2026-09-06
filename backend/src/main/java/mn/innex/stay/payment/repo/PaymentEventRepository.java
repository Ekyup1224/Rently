package mn.innex.stay.payment.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.payment.domain.PaymentEvent;
import mn.innex.stay.payment.domain.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    /** Dedupe check for a redelivered callback. */
    Optional<PaymentEvent> findByProviderAndProviderEventId(PaymentProvider provider,
                                                            String providerEventId);
}

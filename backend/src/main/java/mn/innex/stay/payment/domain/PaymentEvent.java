package mn.innex.stay.payment.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A provider callback, stored raw before it is acted on.
 *
 * <p>Payment callbacks arrive more than once by design, so the raw log is what
 * makes replay safe: {@code (provider, providerEventId)} is unique, giving
 * dedupe, and a payload that failed to process is preserved for replay after a
 * fix rather than lost. It is also the only evidence available when a provider
 * disputes what it sent.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Null when the callback could not be matched to a known payment. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentProvider provider;

    @Column(name = "provider_event_id", length = 128)
    private String providerEventId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error", length = 500)
    private String processingError;

    protected PaymentEvent() {
    }

    public PaymentEvent(PaymentProvider provider, String providerEventId, String eventType,
                        Map<String, Object> payload, boolean signatureVerified) {
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payload = payload == null ? Map.of() : payload;
        this.signatureVerified = signatureVerified;
    }

    public void markProcessed(UUID paymentId) {
        this.paymentId = paymentId;
        this.processedAt = Instant.now();
        this.processingError = null;
    }

    public void markFailed(String error) {
        this.processingError = error == null || error.length() <= 500
                ? error
                : error.substring(0, 500);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getProcessingError() {
        return processingError;
    }
}

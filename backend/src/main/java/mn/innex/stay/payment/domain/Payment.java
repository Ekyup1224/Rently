package mn.innex.stay.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.common.Money;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One attempt to move money for a booking.
 *
 * <p>{@code idempotencyKey} is unique, which is what makes a double-tapped Pay
 * button return the existing attempt instead of creating a second charge.
 * {@code (provider, providerRef)} is unique too, so a redelivered callback can
 * never be attributed to a different row.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** The charge being reversed, on a REFUND row. */
    @Column(name = "parent_payment_id")
    private UUID parentPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentIntent intent = PaymentIntent.CHARGE;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "MNT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentRecordStatus status = PaymentRecordStatus.CREATED;

    /** The provider's own identifier for this transaction. */
    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    /** Provider-specific checkout material: QR text, deeplinks, hosted URLs. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkout_payload")
    private Map<String, Object> checkoutPayload;

    @Column(name = "idempotency_key", nullable = false, length = 80, unique = true)
    private String idempotencyKey;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public static Payment charge(Booking booking, PaymentProvider provider, BigDecimal amount,
                                 String idempotencyKey) {
        Payment payment = new Payment();
        payment.booking = booking;
        payment.provider = provider;
        payment.intent = PaymentIntent.CHARGE;
        payment.amount = Money.of(amount);
        payment.currency = booking.getCurrency();
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    public static Payment refund(Booking booking, Payment original, BigDecimal amount,
                                 String idempotencyKey) {
        Payment payment = new Payment();
        payment.booking = booking;
        payment.provider = original.getProvider();
        payment.intent = PaymentIntent.REFUND;
        payment.parentPaymentId = original.getId();
        payment.amount = Money.of(amount);
        payment.currency = booking.getCurrency();
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markPending(String providerRef, Map<String, Object> checkoutPayload, Instant expiresAt) {
        this.status = PaymentRecordStatus.PENDING;
        this.providerRef = providerRef;
        this.checkoutPayload = checkoutPayload == null ? null : new HashMap<>(checkoutPayload);
        this.expiresAt = expiresAt;
    }

    public void markSucceeded() {
        this.status = PaymentRecordStatus.SUCCEEDED;
        this.paidAt = Instant.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed(String code, String message) {
        this.status = PaymentRecordStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message == null || message.length() <= 500
                ? message
                : message.substring(0, 500);
    }

    public void markExpired() {
        this.status = PaymentRecordStatus.EXPIRED;
    }

    public void markCancelled() {
        this.status = PaymentRecordStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public UUID getParentPaymentId() {
        return parentPaymentId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public PaymentIntent getIntent() {
        return intent;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentRecordStatus getStatus() {
        return status;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public Map<String, Object> getCheckoutPayload() {
        return checkoutPayload;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

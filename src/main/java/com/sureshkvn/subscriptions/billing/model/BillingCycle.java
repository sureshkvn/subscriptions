package com.sureshkvn.subscriptions.billing.model;

import com.sureshkvn.subscriptions.subscription.model.Subscription;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Records a single billing cycle for a {@link Subscription}.
 *
 * <p>One billing cycle is created each time a subscription renews or is invoiced.
 * This provides a full audit trail of all charges.
 *
 * <h2>Discount fields</h2>
 * <ul>
 *   <li>{@code originalAmount} — the plan's base price at the time of invoicing,
 *       before any coupon discounts are applied.</li>
 *   <li>{@code totalDiscountAmount} — the sum of all active coupon discount contributions
 *       computed using the parallel strategy at billing time. Always ≥ 0.</li>
 *   <li>{@code amount} — the final charged amount ({@code originalAmount − totalDiscountAmount}),
 *       guaranteed to be ≥ 0.00 in the subscription's currency.</li>
 * </ul>
 * Both {@code originalAmount} and {@code totalDiscountAmount} are nullable to support
 * existing records created before discount tracking was introduced.
 */
@Entity
@Table(name = "billing_cycles",
        indexes = {
                @Index(name = "idx_billing_subscription", columnList = "subscription_id"),
                @Index(name = "idx_billing_status",       columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    /**
     * The plan's base price at invoice time, before discounts.
     * Nullable for backward compatibility with pre-discount billing records.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal originalAmount;

    /**
     * Sum of all active coupon discount contributions at billing time (parallel strategy).
     * Nullable for backward compatibility. Always ≥ 0 when present.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalDiscountAmount;

    /**
     * Final charged amount after discounts ({@code originalAmount − totalDiscountAmount}),
     * floored at 0.00 in the subscription's currency.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Start of the billing period this cycle covers. */
    @Column(nullable = false)
    private Instant periodStart;

    /** End of the billing period this cycle covers. */
    @Column(nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BillingStatus status = BillingStatus.PENDING;

    /** When the payment was successfully collected. */
    private Instant paidAt;

    /** Reference from the payment processor (e.g., Stripe charge ID). */
    @Column(length = 200)
    private String paymentReference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum BillingStatus {
        PENDING, PAID, FAILED, REFUNDED, VOID
    }
}

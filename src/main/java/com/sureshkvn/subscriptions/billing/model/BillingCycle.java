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
 */
@Entity
@Table(name = "billing_cycles",
        indexes = {
                @Index(name = "idx_billing_subscription", columnList = "subscription_id"),
                @Index(name = "idx_billing_status", columnList = "status")
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

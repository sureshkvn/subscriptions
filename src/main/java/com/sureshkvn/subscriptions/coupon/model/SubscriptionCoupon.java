package com.sureshkvn.subscriptions.coupon.model;

import com.sureshkvn.subscriptions.subscription.model.Subscription;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Join entity representing a coupon applied to a specific subscription.
 *
 * <p>This is a promoted join table (not a simple {@code @ManyToMany}) because it carries
 * meaningful state: the discount snapshot computed at application time, and an {@code active}
 * flag that allows revoking a coupon without destroying the audit record.
 *
 * <p><b>Immutability of discountAmount:</b> The discount is computed once at application time
 * against the plan's base price. Subsequent changes to the coupon's {@code discountValue} do
 * not retroactively affect existing {@code SubscriptionCoupon} records.
 */
@Entity
@Table(name = "subscription_coupons",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_subscription_coupon",
                columnNames = {"subscription_id", "coupon_id"}),
        indexes = {
                @Index(name = "idx_sub_coupon_subscription", columnList = "subscription_id"),
                @Index(name = "idx_sub_coupon_coupon", columnList = "coupon_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    /**
     * Snapshot of the monetary discount this coupon contributed, computed at application time
     * using the parallel strategy against the subscription's plan base price.
     * Stored in the plan's currency.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /** {@code false} means this coupon has been revoked but its record is preserved for auditing. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant appliedAt;
}

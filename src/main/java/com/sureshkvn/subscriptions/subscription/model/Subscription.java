package com.sureshkvn.subscriptions.subscription.model;

import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;
import com.sureshkvn.subscriptions.plan.model.Plan;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a customer's subscription to a {@link Plan}.
 *
 * <p>Lifecycle: PENDING → TRIALING → ACTIVE → PAUSED → CANCELLED
 * <br>Or directly:  PENDING → ACTIVE → CANCELLED
 *
 * <p>Discounts are tracked via {@link #appliedCoupons} — a collection of
 * {@link SubscriptionCoupon} join entities, each carrying a snapshot of the
 * discount that coupon contributed at application time.
 */
@Entity
@Table(name = "subscriptions",
        indexes = {
                @Index(name = "idx_subscription_customer", columnList = "customerId"),
                @Index(name = "idx_subscription_status",   columnList = "status"),
                @Index(name = "idx_subscription_plan",     columnList = "plan_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    /** When the subscription billing officially begins. */
    @Column(nullable = false)
    private Instant startDate;

    /** When the current billing period ends / renews. */
    private Instant currentPeriodEnd;

    /** When the trial ends (null if no trial). */
    private Instant trialEnd;

    /** When the subscription was cancelled (null if active). */
    private Instant cancelledAt;

    /** When the subscription will fully end after cancellation. */
    private Instant cancelAt;

    /** Free-text reason for cancellation. */
    @Column(length = 1000)
    private String cancellationReason;

    /**
     * All coupons applied to this subscription, including revoked ones ({@code active = false}).
     *
     * <p>Use {@link #getActiveCoupons()} to get only the currently active discount coupons.
     */
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubscriptionCoupon> appliedCoupons = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Domain helpers
    // -------------------------------------------------------------------------

    /** Returns only the currently active (non-revoked) coupons on this subscription. */
    public List<SubscriptionCoupon> getActiveCoupons() {
        return appliedCoupons.stream()
                .filter(SubscriptionCoupon::isActive)
                .toList();
    }

    public enum SubscriptionStatus {
        PENDING,
        TRIALING,
        ACTIVE,
        PAUSED,
        CANCELLED,
        EXPIRED
    }
}

package com.sureshkvn.subscriptions.subscription.model;

import com.sureshkvn.subscriptions.plan.model.Plan;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Represents a customer's subscription to a {@link Plan}.
 *
 * <p>Lifecycle: PENDING → TRIALING → ACTIVE → PAUSED → CANCELLED
 * <br>Or directly:  PENDING → ACTIVE → CANCELLED
 */
@Entity
@Table(name = "subscriptions",
        indexes = {
                @Index(name = "idx_subscription_customer", columnList = "customerId"),
                @Index(name = "idx_subscription_status", columnList = "status"),
                @Index(name = "idx_subscription_plan", columnList = "plan_id")
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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum SubscriptionStatus {
        PENDING,
        TRIALING,
        ACTIVE,
        PAUSED,
        CANCELLED,
        EXPIRED
    }
}

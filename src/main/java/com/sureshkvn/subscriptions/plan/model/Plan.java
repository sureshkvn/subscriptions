package com.sureshkvn.subscriptions.plan.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a subscription plan defining pricing and billing interval.
 *
 * <p>Plans are immutable once active — modifications require creating a new plan
 * version to preserve billing history for existing subscriptions.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Billing interval type (e.g., HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingInterval billingInterval;

    /**
     * Number of interval units per billing cycle.
     * For example, billingInterval=HOURLY and intervalCount=6 means every 6 hours.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer intervalCount = 1;

    /**
     * Trial period in days. 0 means no trial.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer trialPeriodDays = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlanStatus status = PlanStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum BillingInterval {
        HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM
    }

    public enum PlanStatus {
        DRAFT, ACTIVE, ARCHIVED
    }
}

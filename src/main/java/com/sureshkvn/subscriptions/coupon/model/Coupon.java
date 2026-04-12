package com.sureshkvn.subscriptions.coupon.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a discount coupon that can be applied to one or more subscriptions.
 *
 * <p><b>Stackability rules:</b>
 * <ul>
 *   <li>If {@code stackable = true}: can coexist with other stackable coupons on the same subscription.</li>
 *   <li>If {@code stackable = false}: must be the only coupon on the subscription — cannot be added
 *       alongside any other coupon, and no other coupon can be added once a non-stackable one is applied.</li>
 * </ul>
 *
 * <p><b>Discount application (parallel strategy):</b>
 * Each coupon's discount is computed independently against the original plan price.
 * Individual discounts are summed and the total is capped so the final amount never falls below zero.
 */
@Entity
@Table(name = "coupons",
        indexes = @Index(name = "idx_coupon_code", columnList = "code", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable unique code customers enter (e.g., SUMMER20, FLAT10). */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    /**
     * The discount magnitude.
     * For {@link DiscountType#PERCENT}: a value between 0 and 100 (exclusive of 0).
     * For {@link DiscountType#FIXED_AMOUNT}: a positive monetary value in the plan's currency.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /**
     * When {@code true}, this coupon can be stacked with other stackable coupons.
     * When {@code false}, this coupon must be the sole coupon on a subscription.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean stackable = true;

    /**
     * Optional minimum subscription base price required for this coupon to be applicable.
     * {@code null} means no minimum.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal minimumAmount;

    /**
     * Maximum number of times this coupon can be redeemed across all subscriptions.
     * {@code null} means unlimited.
     */
    private Integer maxRedemptions;

    /** Current total number of active redemptions. Incremented on apply, not decremented on revoke. */
    @Column(nullable = false)
    @Builder.Default
    private Integer currentRedemptions = 0;

    /**
     * When this coupon expires. {@code null} means it never expires.
     * Validated at application time — no background job flips the status.
     */
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CouponStatus status = CouponStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum DiscountType {
        /** A percentage off the base price (0 < value ≤ 100). */
        PERCENT,
        /** A fixed monetary amount off the base price. */
        FIXED_AMOUNT
    }

    public enum CouponStatus {
        ACTIVE,
        INACTIVE
    }

    // -------------------------------------------------------------------------
    // Domain helpers
    // -------------------------------------------------------------------------

    /** Returns true if this coupon is currently eligible for application. */
    public boolean isEligible() {
        if (status != CouponStatus.ACTIVE) return false;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
        if (maxRedemptions != null && currentRedemptions >= maxRedemptions) return false;
        return true;
    }

    public void incrementRedemptions() {
        this.currentRedemptions++;
    }
}

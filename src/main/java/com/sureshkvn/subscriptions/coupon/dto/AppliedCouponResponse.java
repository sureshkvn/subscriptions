package com.sureshkvn.subscriptions.coupon.dto;

import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO representing a {@link SubscriptionCoupon} — a coupon that has been
 * applied to a specific subscription.
 *
 * <p>Includes both the coupon definition fields and the application-specific fields
 * ({@code discountAmount}, {@code active}, {@code appliedAt}).
 */
public record AppliedCouponResponse(
        Long id,
        String code,
        String description,
        Coupon.DiscountType discountType,
        BigDecimal discountValue,
        boolean stackable,

        /** The monetary discount this coupon contributed, computed at application time. */
        BigDecimal discountAmount,

        /** {@code false} means this coupon has been revoked from the subscription. */
        boolean active,
        Instant appliedAt
) {}

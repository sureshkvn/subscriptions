package com.sureshkvn.subscriptions.coupon.dto;

import com.sureshkvn.subscriptions.coupon.model.Coupon;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO for {@link Coupon} API responses.
 */
public record CouponResponse(
        Long id,
        String code,
        String description,
        Coupon.DiscountType discountType,
        BigDecimal discountValue,
        boolean stackable,
        BigDecimal minimumAmount,
        Integer maxRedemptions,
        Integer currentRedemptions,
        Instant expiresAt,
        Coupon.CouponStatus status,
        Instant createdAt,
        Instant updatedAt
) {}

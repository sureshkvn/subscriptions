package com.sureshkvn.subscriptions.coupon.dto;

import com.sureshkvn.subscriptions.coupon.model.Coupon;

import java.math.BigDecimal;

/**
 * Value object returned by {@code CouponService.validateAndRedeem()} carrying the resolved
 * {@link Coupon} entity and the monetary discount it contributes.
 *
 * <p>This is an internal transfer object between the coupon and subscription domains —
 * it is never serialized to the API.
 *
 * <p>The {@code discountAmount} is computed using the parallel strategy:
 * each coupon is evaluated independently against the subscription's base price.
 * The subscription service sums all {@code discountAmount} values and caps the total
 * so the final price never falls below zero.
 */
public record DiscountResult(
        Coupon coupon,
        BigDecimal discountAmount
) {}

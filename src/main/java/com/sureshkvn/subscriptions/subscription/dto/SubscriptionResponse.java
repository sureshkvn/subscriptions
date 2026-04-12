package com.sureshkvn.subscriptions.subscription.dto;

import com.sureshkvn.subscriptions.coupon.dto.AppliedCouponResponse;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Outbound DTO for subscription API responses.
 *
 * <p>Implemented as a Lombok {@code @Builder} class (rather than a Java record) to allow
 * MapStruct's {@code @AfterMapping} hook to inject computed fields ({@code totalDiscountAmount},
 * {@code effectivePrice}) into the builder before {@code build()} is called — records have
 * immutable canonical constructors that cannot be partially populated post-mapping.
 *
 * <p>{@code totalDiscountAmount} is the sum of the {@code discountAmount} of all
 * <em>active</em> coupons, computed using the parallel strategy against the plan's base price.
 * {@code effectivePrice} is the base price minus this total, floored at 0.00 in the
 * plan's currency.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private Long id;
    private String customerId;
    private PlanResponse plan;
    private Subscription.SubscriptionStatus status;
    private Instant startDate;
    private Instant currentPeriodEnd;
    private Instant trialEnd;
    private Instant cancelledAt;
    private Instant cancelAt;
    private String cancellationReason;

    /** All applied coupons, including revoked ones. Check {@code active} to filter. */
    private List<AppliedCouponResponse> appliedCoupons;

    /** Sum of active coupon discount contributions (parallel strategy). */
    private BigDecimal totalDiscountAmount;

    /** Plan base price minus total discount, floored at 0.00 in the plan's currency. */
    private BigDecimal effectivePrice;

    private Instant createdAt;
    private Instant updatedAt;
}

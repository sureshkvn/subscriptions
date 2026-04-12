package com.sureshkvn.subscriptions.billing.dto;

import com.sureshkvn.subscriptions.billing.model.BillingCycle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO for billing cycle API responses.
 *
 * <p>{@code originalAmount} and {@code totalDiscountAmount} are nullable for backward
 * compatibility with billing records created before discount tracking was introduced.
 */
public record BillingCycleResponse(
        Long id,
        Long subscriptionId,

        /** Plan base price before any coupon discounts. Null for legacy records. */
        BigDecimal originalAmount,

        /** Sum of all coupon discount contributions (parallel strategy). Null for legacy records. */
        BigDecimal totalDiscountAmount,

        /** Final charged amount (originalAmount − totalDiscountAmount), floored at 0.00. */
        BigDecimal amount,

        String currency,
        Instant periodStart,
        Instant periodEnd,
        BillingCycle.BillingStatus status,
        Instant paidAt,
        String paymentReference,
        Instant createdAt
) {}

package com.sureshkvn.subscriptions.billing.dto;

import com.sureshkvn.subscriptions.billing.model.BillingCycle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO for billing cycle API responses.
 */
public record BillingCycleResponse(
        Long id,
        Long subscriptionId,
        BigDecimal amount,
        String currency,
        Instant periodStart,
        Instant periodEnd,
        BillingCycle.BillingStatus status,
        Instant paidAt,
        String paymentReference,
        Instant createdAt
) {}

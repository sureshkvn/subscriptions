package com.sureshkvn.subscriptions.subscription.dto;

import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;

import java.time.Instant;

/**
 * Outbound DTO for subscription API responses.
 */
public record SubscriptionResponse(
        Long id,
        String customerId,
        PlanResponse plan,
        Subscription.SubscriptionStatus status,
        Instant startDate,
        Instant currentPeriodEnd,
        Instant trialEnd,
        Instant cancelledAt,
        Instant cancelAt,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {}

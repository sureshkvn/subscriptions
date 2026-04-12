package com.sureshkvn.subscriptions.plan.dto;

import com.sureshkvn.subscriptions.plan.model.Plan;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO for {@link Plan} API responses.
 */
public record PlanResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        Plan.BillingInterval billingInterval,
        Integer intervalCount,
        Integer trialPeriodDays,
        Plan.PlanStatus status,
        Instant createdAt,
        Instant updatedAt
) {}

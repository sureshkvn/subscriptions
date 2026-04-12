package com.sureshkvn.subscriptions.plan.dto;

import com.sureshkvn.subscriptions.plan.model.Plan;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Inbound DTO for creating or updating a {@link Plan}.
 */
@Builder
public record PlanRequest(

        @NotBlank(message = "Plan name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Price must be zero or greater")
        @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
        BigDecimal price,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
        String currency,

        @NotNull(message = "Billing interval is required")
        Plan.BillingInterval billingInterval,

        @Min(value = 1, message = "Interval count must be at least 1")
        @Max(value = 365, message = "Interval count cannot exceed 365")
        Integer intervalCount,

        @Min(value = 0, message = "Trial period cannot be negative")
        @Max(value = 365, message = "Trial period cannot exceed 365 days")
        Integer trialPeriodDays
) {
    public PlanRequest {
        intervalCount = (intervalCount == null) ? 1 : intervalCount;
        trialPeriodDays = (trialPeriodDays == null) ? 0 : trialPeriodDays;
        currency = (currency != null) ? currency.toUpperCase() : null;
    }
}

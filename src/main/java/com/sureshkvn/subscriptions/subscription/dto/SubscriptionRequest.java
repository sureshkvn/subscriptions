package com.sureshkvn.subscriptions.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;

/**
 * Inbound DTO for creating a new subscription.
 */
@Builder
public record SubscriptionRequest(

        @NotBlank(message = "Customer ID is required")
        @Size(max = 100, message = "Customer ID cannot exceed 100 characters")
        String customerId,

        @NotNull(message = "Plan ID is required")
        Long planId,

        /** Optional override for when the subscription starts; defaults to now. */
        Instant startDate
) {
    public SubscriptionRequest {
        startDate = (startDate == null) ? Instant.now() : startDate;
    }
}

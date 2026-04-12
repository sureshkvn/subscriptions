package com.sureshkvn.subscriptions.subscription.dto;

import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;

/**
 * Inbound DTO for cancelling a subscription.
 */
@Builder
public record CancelSubscriptionRequest(

        @Size(max = 1000, message = "Cancellation reason cannot exceed 1000 characters")
        String reason,

        /**
         * When to cancel: immediately (null = end of current period),
         * or a future {@link Instant}.
         */
        Instant cancelAt
) {}

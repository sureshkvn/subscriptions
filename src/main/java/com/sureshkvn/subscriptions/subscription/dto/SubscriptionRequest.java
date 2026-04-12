package com.sureshkvn.subscriptions.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Inbound DTO for creating a new subscription.
 *
 * <p>{@code couponCodes} is optional. If provided, all codes are validated and redeemed
 * atomically within the same transaction as subscription creation:
 * <ul>
 *   <li>A single non-stackable code is allowed on its own.</li>
 *   <li>Multiple codes are allowed only if every code in the list is stackable.</li>
 * </ul>
 */
@Builder
public record SubscriptionRequest(

        @NotBlank(message = "Customer ID is required")
        @Size(max = 100, message = "Customer ID cannot exceed 100 characters")
        String customerId,

        @NotNull(message = "Plan ID is required")
        Long planId,

        /** Optional override for when the subscription starts; defaults to now. */
        Instant startDate,

        /**
         * Optional list of coupon codes to apply at creation time.
         * {@code null} and empty list are both treated as "no coupons".
         */
        List<String> couponCodes
) {
    public SubscriptionRequest {
        startDate = (startDate == null) ? Instant.now() : startDate;
        couponCodes = (couponCodes == null) ? List.of() : couponCodes;
    }
}

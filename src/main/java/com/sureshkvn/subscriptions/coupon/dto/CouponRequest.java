package com.sureshkvn.subscriptions.coupon.dto;

import com.sureshkvn.subscriptions.coupon.model.Coupon;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound DTO for creating a new {@link Coupon}.
 *
 * <p>Cross-field validation (e.g., PERCENT value ≤ 100) is enforced in the service layer
 * to keep annotations focused on structural constraints.
 */
@Builder
public record CouponRequest(

        @NotBlank(message = "Coupon code is required")
        @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$",
                message = "Code must contain only uppercase letters, digits, hyphens, or underscores")
        String code,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @NotNull(message = "Discount type is required")
        Coupon.DiscountType discountType,

        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount value must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Discount value format is invalid")
        BigDecimal discountValue,

        /** Whether this coupon can be stacked with others. Defaults to {@code true}. */
        Boolean stackable,

        /**
         * Optional minimum base price the subscription plan must have for this coupon to apply.
         */
        @DecimalMin(value = "0.00", message = "Minimum amount cannot be negative")
        BigDecimal minimumAmount,

        /**
         * Maximum number of redemptions allowed. {@code null} = unlimited.
         */
        @Min(value = 1, message = "Max redemptions must be at least 1")
        Integer maxRedemptions,

        /**
         * Optional expiry timestamp. Must be in the future at creation time.
         */
        Instant expiresAt
) {
    public CouponRequest {
        stackable = (stackable == null) ? Boolean.TRUE : stackable;
        code = (code != null) ? code.toUpperCase().strip() : null;
    }
}

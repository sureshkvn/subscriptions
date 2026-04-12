package com.sureshkvn.subscriptions.subscription.controller;

import com.sureshkvn.subscriptions.common.response.ApiResponse;
import com.sureshkvn.subscriptions.subscription.dto.CancelSubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for subscription lifecycle management.
 *
 * <p>Base URL: {@code /api/v1/subscriptions}
 *
 * <h2>Coupon endpoints</h2>
 * <p>Coupon application and revocation live here (not on {@code /v1/coupons}) because
 * these actions mutate subscription state. The coupon controller manages coupon definitions.
 */
@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Customer subscription lifecycle management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // -------------------------------------------------------------------------
    // Core subscription CRUD / lifecycle
    // -------------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Create a new subscription (optionally with coupon codes)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @Valid @RequestBody SubscriptionRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(subscriptionService.createSubscription(request),
                        "Subscription created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a subscription by ID")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscriptionById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getSubscriptionById(id)));
    }

    @GetMapping
    @Operation(summary = "List subscriptions filtered by customer ID or status")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getSubscriptions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) Subscription.SubscriptionStatus status) {

        List<SubscriptionResponse> results = (customerId != null)
                ? subscriptionService.getSubscriptionsByCustomer(customerId)
                : subscriptionService.getSubscriptionsByStatus(status);

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a PENDING subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.activateSubscription(id), "Subscription activated"));
    }

    @PatchMapping("/{id}/pause")
    @Operation(summary = "Pause an ACTIVE subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> pause(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.pauseSubscription(id), "Subscription paused"));
    }

    @PatchMapping("/{id}/resume")
    @Operation(summary = "Resume a PAUSED subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.resumeSubscription(id), "Subscription resumed"));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelSubscriptionRequest request) {
        CancelSubscriptionRequest cancelRequest = (request != null)
                ? request : CancelSubscriptionRequest.builder().build();
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.cancelSubscription(id, cancelRequest),
                "Subscription cancelled"));
    }

    // -------------------------------------------------------------------------
    // Coupon management on subscriptions
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/coupons")
    @Operation(summary = "Apply a coupon to an existing subscription",
               description = """
                       Stackability rules are enforced:
                       - A non-stackable coupon cannot be added if any coupon is already present.
                       - No coupon can be added if a non-stackable coupon is already applied.
                       - The same coupon code cannot be applied twice.
                       """)
    public ResponseEntity<ApiResponse<SubscriptionResponse>> addCoupon(
            @PathVariable Long id,
            @RequestParam String couponCode) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.addCoupon(id, couponCode),
                "Coupon '" + couponCode.toUpperCase() + "' applied successfully"));
    }

    @DeleteMapping("/{id}/coupons/{couponCode}")
    @Operation(summary = "Revoke a coupon from a subscription",
               description = """
                       Marks the coupon as inactive on this subscription.
                       The audit record is preserved. The coupon's redemption count is not decremented.
                       """)
    public ResponseEntity<ApiResponse<SubscriptionResponse>> removeCoupon(
            @PathVariable Long id,
            @PathVariable String couponCode) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.removeCoupon(id, couponCode),
                "Coupon '" + couponCode.toUpperCase() + "' revoked successfully"));
    }
}

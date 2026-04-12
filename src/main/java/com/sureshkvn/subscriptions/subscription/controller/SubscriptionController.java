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
 */
@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Customer subscription lifecycle management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "Create a new subscription for a customer")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse response = subscriptionService.createSubscription(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Subscription created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a subscription by ID")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscriptionById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getSubscriptionById(id)));
    }

    @GetMapping
    @Operation(summary = "List subscriptions, filtered by customer ID or status")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getSubscriptions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) Subscription.SubscriptionStatus status) {

        List<SubscriptionResponse> results;
        if (customerId != null) {
            results = subscriptionService.getSubscriptionsByCustomer(customerId);
        } else if (status != null) {
            results = subscriptionService.getSubscriptionsByStatus(status);
        } else {
            results = subscriptionService.getSubscriptionsByStatus(null);
        }
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a PENDING subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(subscriptionService.activateSubscription(id),
                        "Subscription activated"));
    }

    @PatchMapping("/{id}/pause")
    @Operation(summary = "Pause an ACTIVE subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> pause(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(subscriptionService.pauseSubscription(id),
                        "Subscription paused"));
    }

    @PatchMapping("/{id}/resume")
    @Operation(summary = "Resume a PAUSED subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(subscriptionService.resumeSubscription(id),
                        "Subscription resumed"));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelSubscriptionRequest request) {
        CancelSubscriptionRequest cancelRequest = (request != null) ? request
                : CancelSubscriptionRequest.builder().build();
        return ResponseEntity.ok(
                ApiResponse.success(subscriptionService.cancelSubscription(id, cancelRequest),
                        "Subscription cancelled"));
    }
}

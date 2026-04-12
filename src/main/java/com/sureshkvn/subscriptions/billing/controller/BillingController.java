package com.sureshkvn.subscriptions.billing.controller;

import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import com.sureshkvn.subscriptions.billing.service.BillingService;
import com.sureshkvn.subscriptions.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for billing cycle management.
 *
 * <p>Base URL: {@code /api/v1/billing}
 */
@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Billing cycle tracking and payment management")
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/subscriptions/{subscriptionId}/cycles")
    @Operation(summary = "List all billing cycles for a subscription")
    public ResponseEntity<ApiResponse<List<BillingCycleResponse>>> getBillingCycles(
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getBillingCyclesBySubscription(subscriptionId)));
    }

    @GetMapping("/cycles/{id}")
    @Operation(summary = "Get a billing cycle by ID")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> getBillingCycleById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getBillingCycleById(id)));
    }

    @GetMapping("/cycles")
    @Operation(summary = "List billing cycles by status")
    public ResponseEntity<ApiResponse<List<BillingCycleResponse>>> getBillingCyclesByStatus(
            @RequestParam(required = false) BillingCycle.BillingStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getBillingCyclesByStatus(status)));
    }

    @PatchMapping("/cycles/{id}/pay")
    @Operation(summary = "Mark a billing cycle as paid")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> markAsPaid(
            @PathVariable Long id,
            @RequestParam String paymentReference) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.markAsPaid(id, paymentReference),
                "Billing cycle marked as paid"));
    }

    @PatchMapping("/cycles/{id}/void")
    @Operation(summary = "Void a billing cycle")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> voidCycle(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.voidBillingCycle(id),
                "Billing cycle voided"));
    }
}

package com.sureshkvn.subscriptions.plan.controller;

import com.sureshkvn.subscriptions.common.response.ApiResponse;
import com.sureshkvn.subscriptions.plan.dto.PlanRequest;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.plan.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for subscription plan management.
 *
 * <p>Base URL: {@code /api/v1/plans}
 */
@RestController
@RequestMapping("/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Subscription plan lifecycle management")
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @Operation(summary = "Create a new subscription plan")
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @Valid @RequestBody PlanRequest request) {
        PlanResponse response = planService.createPlan(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Plan created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a plan by ID")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlanById(id)));
    }

    @GetMapping
    @Operation(summary = "List all plans, optionally filtered by status")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getAllPlans(
            @RequestParam(required = false) Plan.PlanStatus status) {
        List<PlanResponse> plans = (status != null)
                ? planService.getPlansByStatus(status)
                : planService.getAllPlans();
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing plan")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(planService.updatePlan(id, request), "Plan updated successfully"));
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive a plan (soft delete)")
    public ResponseEntity<ApiResponse<PlanResponse>> archivePlan(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(planService.archivePlan(id), "Plan archived successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a plan (only DRAFT or ARCHIVED plans)")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Plan deleted successfully"));
    }
}

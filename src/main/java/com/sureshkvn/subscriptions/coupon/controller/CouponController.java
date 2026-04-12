package com.sureshkvn.subscriptions.coupon.controller;

import com.sureshkvn.subscriptions.common.response.ApiResponse;
import com.sureshkvn.subscriptions.coupon.dto.CouponRequest;
import com.sureshkvn.subscriptions.coupon.dto.CouponResponse;
import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for coupon definition management.
 *
 * <p>Base URL: {@code /api/v1/coupons}
 *
 * <p>Note: applying and revoking coupons on a subscription is handled by
 * {@link com.sureshkvn.subscriptions.subscription.controller.SubscriptionController}
 * at {@code /api/v1/subscriptions/{id}/coupons}, because those actions mutate
 * subscription state, not the coupon definition.
 */
@RestController
@RequestMapping("/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon definition management")
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @Operation(summary = "Create a new coupon")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(couponService.createCoupon(request),
                        "Coupon created successfully"));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a coupon by code")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponByCode(
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(
                couponService.getCouponByCode(code.toUpperCase())));
    }

    @GetMapping
    @Operation(summary = "List all coupons, optionally filtered by status")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getAllCoupons(
            @RequestParam(required = false) Coupon.CouponStatus status) {
        List<CouponResponse> coupons = (status != null)
                ? couponService.getCouponsByStatus(status)
                : couponService.getAllCoupons();
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    @PatchMapping("/{code}/deactivate")
    @Operation(summary = "Deactivate a coupon (prevents future redemptions)")
    public ResponseEntity<ApiResponse<CouponResponse>> deactivateCoupon(
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(
                couponService.deactivateCoupon(code.toUpperCase()),
                "Coupon deactivated successfully"));
    }
}

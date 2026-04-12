package com.sureshkvn.subscriptions.coupon.service;

import com.sureshkvn.subscriptions.coupon.dto.CouponRequest;
import com.sureshkvn.subscriptions.coupon.dto.CouponResponse;
import com.sureshkvn.subscriptions.coupon.dto.DiscountResult;
import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for coupon lifecycle management and discount application.
 */
public interface CouponService {

    // -------------------------------------------------------------------------
    // Coupon management
    // -------------------------------------------------------------------------

    CouponResponse createCoupon(CouponRequest request);

    CouponResponse getCouponByCode(String code);

    List<CouponResponse> getAllCoupons();

    List<CouponResponse> getCouponsByStatus(Coupon.CouponStatus status);

    CouponResponse deactivateCoupon(String code);

    // -------------------------------------------------------------------------
    // Discount application
    // -------------------------------------------------------------------------

    /**
     * Validates and redeems a list of coupon codes for a new subscription being created.
     *
     * <p>Performs the following checks for each coupon:
     * <ul>
     *   <li>Exists</li>
     *   <li>Is {@link Coupon.CouponStatus#ACTIVE} and not expired</li>
     *   <li>Has not exceeded its redemption limit</li>
     *   <li>Meets the minimum amount requirement against {@code baseAmount}</li>
     * </ul>
     *
     * <p>Then validates the set as a whole for stackability:
     * <ul>
     *   <li>If any coupon is non-stackable, the list must contain exactly one coupon</li>
     * </ul>
     *
     * <p>On success, increments {@code currentRedemptions} for each coupon and returns
     * a {@link DiscountResult} per coupon with the individual discount contribution
     * computed via the parallel strategy.
     *
     * @param codes      list of coupon codes to apply (may be empty)
     * @param baseAmount the subscription plan's base price used for discount calculation
     * @return list of discount results, one per code (empty if codes is empty)
     * @throws com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException if any code is unknown
     * @throws com.sureshkvn.subscriptions.common.exception.BusinessRuleException      if any validation fails
     */
    List<DiscountResult> validateAndRedeem(List<String> codes, BigDecimal baseAmount);

    /**
     * Validates and redeems a single coupon code being added to an <em>existing</em>
     * subscription that may already have active coupons.
     *
     * <p>Applies all individual coupon checks (above) plus stackability checks against
     * {@code existingActiveCoupons}:
     * <ul>
     *   <li>Cannot add a non-stackable coupon if the subscription already has any coupon</li>
     *   <li>Cannot add any coupon if the subscription already holds a non-stackable coupon</li>
     *   <li>Cannot add the same coupon code twice</li>
     * </ul>
     *
     * @param code                  the coupon code to apply
     * @param baseAmount            the subscription plan's base price
     * @param existingActiveCoupons the subscription's currently active {@link SubscriptionCoupon} records
     * @return the discount result for the new coupon
     */
    DiscountResult validateAndRedeemOne(String code, BigDecimal baseAmount,
                                        List<SubscriptionCoupon> existingActiveCoupons);
}

package com.sureshkvn.subscriptions.subscription.service;

import com.sureshkvn.subscriptions.subscription.dto.CancelSubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;

import java.util.List;

/**
 * Service interface for subscription lifecycle management.
 */
public interface SubscriptionService {

    SubscriptionResponse createSubscription(SubscriptionRequest request);

    SubscriptionResponse getSubscriptionById(Long id);

    List<SubscriptionResponse> getSubscriptionsByCustomer(String customerId);

    List<SubscriptionResponse> getSubscriptionsByStatus(Subscription.SubscriptionStatus status);

    SubscriptionResponse activateSubscription(Long id);

    SubscriptionResponse pauseSubscription(Long id);

    SubscriptionResponse resumeSubscription(Long id);

    SubscriptionResponse cancelSubscription(Long id, CancelSubscriptionRequest request);

    /**
     * Applies an additional coupon to an existing subscription.
     *
     * <p>Validates stackability against all currently active coupons on the subscription.
     * The new coupon's discount is computed against the plan's base price and recorded
     * in a new {@link com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon} entry.
     *
     * @param subscriptionId the target subscription
     * @param couponCode     the coupon code to apply
     * @return updated subscription response including the new coupon
     */
    SubscriptionResponse addCoupon(Long subscriptionId, String couponCode);

    /**
     * Revokes a coupon from a subscription.
     *
     * <p>Marks the {@link com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon}
     * as {@code active = false}. The record is preserved for auditing. The coupon's
     * {@code currentRedemptions} count is NOT decremented (revocation ≠ non-use).
     *
     * @param subscriptionId the target subscription
     * @param couponCode     the coupon code to revoke
     * @return updated subscription response with the coupon marked inactive
     */
    SubscriptionResponse removeCoupon(Long subscriptionId, String couponCode);
}

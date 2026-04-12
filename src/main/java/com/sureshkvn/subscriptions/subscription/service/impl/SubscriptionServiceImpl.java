package com.sureshkvn.subscriptions.subscription.service.impl;

import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import com.sureshkvn.subscriptions.coupon.dto.DiscountResult;
import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;
import com.sureshkvn.subscriptions.coupon.repository.SubscriptionCouponRepository;
import com.sureshkvn.subscriptions.coupon.service.CouponService;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.plan.repository.PlanRepository;
import com.sureshkvn.subscriptions.subscription.dto.CancelSubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.mapper.SubscriptionMapper;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.repository.SubscriptionRepository;
import com.sureshkvn.subscriptions.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Default implementation of {@link SubscriptionService}.
 *
 * <h2>Coupon application flow</h2>
 * <ol>
 *   <li>Coupon codes are resolved and validated by {@link CouponService}.</li>
 *   <li>Each code is redeemed atomically within the same transaction as subscription creation.</li>
 *   <li>A {@link SubscriptionCoupon} join record is created per code, storing the discount
 *       snapshot computed at application time.</li>
 *   <li>The final effective price (plan price − total discount, floored at 0.00) is visible
 *       in the {@link SubscriptionResponse} via the mapper's computed fields.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final List<Subscription.SubscriptionStatus> ACTIVE_STATUSES = List.of(
            Subscription.SubscriptionStatus.ACTIVE,
            Subscription.SubscriptionStatus.TRIALING,
            Subscription.SubscriptionStatus.PENDING
    );

    private final SubscriptionRepository        subscriptionRepository;
    private final PlanRepository                planRepository;
    private final SubscriptionMapper            subscriptionMapper;
    private final CouponService                 couponService;
    private final SubscriptionCouponRepository  subscriptionCouponRepository;

    // -------------------------------------------------------------------------
    // Core lifecycle
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionRequest request) {
        log.info("Creating subscription for customer: {} on plan: {}",
                request.customerId(), request.planId());

        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", request.planId()));

        if (plan.getStatus() != Plan.PlanStatus.ACTIVE) {
            throw new BusinessRuleException("Cannot subscribe to a plan that is not active");
        }

        if (subscriptionRepository.existsByCustomerIdAndPlanIdAndStatusIn(
                request.customerId(), request.planId(), ACTIVE_STATUSES)) {
            throw new BusinessRuleException(
                    "Customer already has an active subscription for this plan");
        }

        Instant start = request.startDate();
        Subscription.SubscriptionStatus initialStatus;
        Instant trialEnd = null;

        if (plan.getTrialPeriodDays() > 0) {
            initialStatus = Subscription.SubscriptionStatus.TRIALING;
            trialEnd = start.plus(plan.getTrialPeriodDays(), ChronoUnit.DAYS);
        } else {
            initialStatus = Subscription.SubscriptionStatus.ACTIVE;
        }

        Subscription subscription = Subscription.builder()
                .customerId(request.customerId())
                .plan(plan)
                .status(initialStatus)
                .startDate(start)
                .trialEnd(trialEnd)
                .currentPeriodEnd(calculateNextPeriodEnd(plan, start))
                .build();

        // Validate, redeem, and attach coupons within the same transaction
        if (!request.couponCodes().isEmpty()) {
            List<DiscountResult> results = couponService.validateAndRedeem(
                    request.couponCodes(), plan.getPrice());
            attachCoupons(subscription, results);
        }

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created with id: {}", saved.getId());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    public SubscriptionResponse getSubscriptionById(Long id) {
        return subscriptionMapper.toResponse(findOrThrow(id));
    }

    @Override
    public List<SubscriptionResponse> getSubscriptionsByCustomer(String customerId) {
        return subscriptionRepository.findAllByCustomerId(customerId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    @Override
    public List<SubscriptionResponse> getSubscriptionsByStatus(Subscription.SubscriptionStatus status) {
        return subscriptionRepository.findAllByStatus(status).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionResponse activateSubscription(Long id) {
        Subscription sub = findOrThrow(id);
        assertStatus(sub, Subscription.SubscriptionStatus.PENDING,
                "Only PENDING subscriptions can be activated");
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        return subscriptionMapper.toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse pauseSubscription(Long id) {
        Subscription sub = findOrThrow(id);
        assertStatus(sub, Subscription.SubscriptionStatus.ACTIVE,
                "Only ACTIVE subscriptions can be paused");
        sub.setStatus(Subscription.SubscriptionStatus.PAUSED);
        return subscriptionMapper.toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse resumeSubscription(Long id) {
        Subscription sub = findOrThrow(id);
        assertStatus(sub, Subscription.SubscriptionStatus.PAUSED,
                "Only PAUSED subscriptions can be resumed");
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        return subscriptionMapper.toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse cancelSubscription(Long id, CancelSubscriptionRequest request) {
        log.info("Cancelling subscription id: {}", id);
        Subscription sub = findOrThrow(id);

        if (sub.getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
            throw new BusinessRuleException("Subscription is already cancelled");
        }
        if (sub.getStatus() == Subscription.SubscriptionStatus.EXPIRED) {
            throw new BusinessRuleException("Cannot cancel an expired subscription");
        }

        sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(Instant.now());
        sub.setCancelAt(request.cancelAt());
        sub.setCancellationReason(request.reason());
        return subscriptionMapper.toResponse(subscriptionRepository.save(sub));
    }

    // -------------------------------------------------------------------------
    // Coupon management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubscriptionResponse addCoupon(Long subscriptionId, String couponCode) {
        log.info("Adding coupon '{}' to subscription id: {}", couponCode, subscriptionId);
        Subscription subscription = findOrThrow(subscriptionId);

        if (subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED
                || subscription.getStatus() == Subscription.SubscriptionStatus.EXPIRED) {
            throw new BusinessRuleException(
                    "Cannot add a coupon to a " + subscription.getStatus() + " subscription");
        }

        List<SubscriptionCoupon> existingActive = subscription.getActiveCoupons();
        DiscountResult result = couponService.validateAndRedeemOne(
                couponCode.toUpperCase(), subscription.getPlan().getPrice(), existingActive);

        SubscriptionCoupon sc = SubscriptionCoupon.builder()
                .subscription(subscription)
                .coupon(result.coupon())
                .discountAmount(result.discountAmount())
                .build();

        subscription.getAppliedCoupons().add(sc);
        return subscriptionMapper.toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public SubscriptionResponse removeCoupon(Long subscriptionId, String couponCode) {
        log.info("Removing coupon '{}' from subscription id: {}", couponCode, subscriptionId);

        SubscriptionCoupon sc = subscriptionCouponRepository
                .findBySubscriptionIdAndCouponCodeAndActiveTrue(subscriptionId,
                        couponCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active coupon '" + couponCode + "' not found on subscription "
                                + subscriptionId));

        sc.setActive(false);
        subscriptionCouponRepository.save(sc);

        return subscriptionMapper.toResponse(findOrThrow(subscriptionId));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Subscription findOrThrow(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", id));
    }

    private void assertStatus(Subscription sub,
                               Subscription.SubscriptionStatus expected,
                               String message) {
        if (sub.getStatus() != expected) {
            throw new BusinessRuleException(
                    message + " (current status: " + sub.getStatus() + ")");
        }
    }

    /**
     * Attaches a list of {@link DiscountResult} as {@link SubscriptionCoupon} records
     * to the given subscription. Called before the subscription is persisted so that
     * Hibernate saves everything in one flush.
     */
    private void attachCoupons(Subscription subscription, List<DiscountResult> results) {
        results.forEach(r -> {
            SubscriptionCoupon sc = SubscriptionCoupon.builder()
                    .subscription(subscription)
                    .coupon(r.coupon())
                    .discountAmount(r.discountAmount())
                    .build();
            subscription.getAppliedCoupons().add(sc);
            log.debug("Attached coupon '{}' with discount {} to subscription",
                    r.coupon().getCode(), r.discountAmount());
        });
    }

    private Instant calculateNextPeriodEnd(Plan plan, Instant from) {
        int count = plan.getIntervalCount();
        return switch (plan.getBillingInterval()) {
            case HOURLY  -> from.plus(count, ChronoUnit.HOURS);
            case DAILY   -> from.plus(count, ChronoUnit.DAYS);
            case WEEKLY  -> from.plus((long) count * 7,   ChronoUnit.DAYS);
            case MONTHLY -> from.plus((long) count * 30,  ChronoUnit.DAYS);
            case YEARLY  -> from.plus((long) count * 365, ChronoUnit.DAYS);
            case CUSTOM  -> from.plus(count, ChronoUnit.DAYS);
        };
    }
}

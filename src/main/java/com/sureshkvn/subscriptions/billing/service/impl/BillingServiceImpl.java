package com.sureshkvn.subscriptions.billing.service.impl;

import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
import com.sureshkvn.subscriptions.billing.mapper.BillingMapper;
import com.sureshkvn.subscriptions.billing.messaging.BillingDueEvent;
import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import com.sureshkvn.subscriptions.billing.payment.PaymentDeclinedException;
import com.sureshkvn.subscriptions.billing.payment.PaymentGateway;
import com.sureshkvn.subscriptions.billing.payment.PaymentGatewayException;
import com.sureshkvn.subscriptions.billing.payment.PaymentResult;
import com.sureshkvn.subscriptions.billing.repository.BillingCycleRepository;
import com.sureshkvn.subscriptions.billing.service.BillingService;
import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import com.sureshkvn.subscriptions.coupon.service.DiscountCalculator;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.model.SubscriptionCoupon;
import com.sureshkvn.subscriptions.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Default implementation of {@link BillingService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingServiceImpl implements BillingService {

    private final BillingCycleRepository billingCycleRepository;
    private final BillingMapper          billingMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentGateway         paymentGateway;
    private final DiscountCalculator     discountCalculator;

    @Override
    public List<BillingCycleResponse> getBillingCyclesBySubscription(Long subscriptionId) {
        return billingCycleRepository
                .findAllBySubscriptionIdOrderByCreatedAtDesc(subscriptionId)
                .stream()
                .map(billingMapper::toResponse)
                .toList();
    }

    @Override
    public BillingCycleResponse getBillingCycleById(Long id) {
        return billingMapper.toResponse(findOrThrow(id));
    }

    @Override
    public List<BillingCycleResponse> getBillingCyclesByStatus(BillingCycle.BillingStatus status) {
        return billingCycleRepository.findAllByStatus(status).stream()
                .map(billingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BillingCycleResponse markAsPaid(Long id, String paymentReference) {
        log.info("Marking billing cycle {} as paid with reference: {}", id, paymentReference);
        BillingCycle cycle = findOrThrow(id);

        if (cycle.getStatus() != BillingCycle.BillingStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING billing cycles can be marked as paid (current: "
                            + cycle.getStatus() + ")");
        }

        cycle.setStatus(BillingCycle.BillingStatus.PAID);
        cycle.setPaidAt(Instant.now());
        cycle.setPaymentReference(paymentReference);
        return billingMapper.toResponse(billingCycleRepository.save(cycle));
    }

    @Override
    @Transactional
    public BillingCycleResponse voidBillingCycle(Long id) {
        log.info("Voiding billing cycle id: {}", id);
        BillingCycle cycle = findOrThrow(id);

        if (cycle.getStatus() == BillingCycle.BillingStatus.PAID) {
            throw new BusinessRuleException("Cannot void a PAID billing cycle. Use refund instead.");
        }
        if (cycle.getStatus() == BillingCycle.BillingStatus.VOID) {
            throw new BusinessRuleException("Billing cycle is already voided");
        }

        cycle.setStatus(BillingCycle.BillingStatus.VOID);
        return billingMapper.toResponse(billingCycleRepository.save(cycle));
    }

    // -------------------------------------------------------------------------
    // Pub/Sub event processing
    // -------------------------------------------------------------------------

    /**
     * Processes a billing-due event end-to-end with idempotency protection.
     *
     * <p>The method runs in its own transaction. If the payment gateway throws a
     * <em>transient</em> {@link PaymentGatewayException}, the transaction is rolled back
     * so the billing cycle stays {@code PENDING} and Pub/Sub can retry delivery.
     */
    @Override
    @Transactional
    public void processBillingCycle(BillingDueEvent event) {
        Long subscriptionId = event.subscriptionId();
        Instant periodStart = event.periodStart();

        // --- 1. Idempotency check (fast path) ---
        if (billingCycleRepository.findBySubscriptionIdAndPeriodStart(subscriptionId, periodStart)
                .isPresent()) {
            log.info("Billing cycle already exists for subscription={} periodStart={} — skipping",
                    subscriptionId, periodStart);
            return;
        }

        // --- 2. Load subscription ---
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", subscriptionId));

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            log.warn("Subscription={} is not ACTIVE (status={}), skipping billing",
                    subscriptionId, subscription.getStatus());
            return;
        }

        // --- 3. Compute discount amounts ---
        BigDecimal basePrice   = subscription.getPlan().getPrice();
        String     currency    = subscription.getPlan().getCurrency();

        List<com.sureshkvn.subscriptions.coupon.model.Coupon> activeCoupons =
                subscription.getActiveCoupons().stream()
                        .map(SubscriptionCoupon::getCoupon)
                        .toList();

        BigDecimal totalDiscount = discountCalculator.computeTotalDiscount(basePrice, activeCoupons);
        BigDecimal chargeAmount  = discountCalculator.computeFinalAmount(basePrice, activeCoupons);

        // --- 4. Create PENDING billing cycle (idempotency guard via unique constraint) ---
        BillingCycle cycle;
        try {
            cycle = billingCycleRepository.saveAndFlush(
                    BillingCycle.builder()
                            .subscription(subscription)
                            .originalAmount(basePrice)
                            .totalDiscountAmount(totalDiscount)
                            .amount(chargeAmount)
                            .currency(currency)
                            .periodStart(periodStart)
                            .periodEnd(event.periodEnd())
                            .status(BillingCycle.BillingStatus.PENDING)
                            .build());
        } catch (DataIntegrityViolationException e) {
            // Race condition: another task created this cycle concurrently.
            log.warn("Duplicate billing cycle detected for subscription={} periodStart={} — skipping",
                    subscriptionId, periodStart);
            return;
        }

        // --- 5. Charge via payment gateway ---
        try {
            PaymentResult result = paymentGateway.charge(cycle);
            cycle.setStatus(BillingCycle.BillingStatus.PAID);
            cycle.setPaidAt(Instant.now());
            cycle.setPaymentReference(result.paymentReference());
            billingCycleRepository.save(cycle);

            // Advance the subscription period
            subscription.setCurrentPeriodEnd(event.periodEnd());
            subscriptionRepository.save(subscription);

            log.info("Billing cycle={} PAID for subscription={} amount={} {}",
                    cycle.getId(), subscriptionId, chargeAmount, currency);

        } catch (PaymentDeclinedException ex) {
            // Permanent decline — mark FAILED and re-throw so the controller can ack.
            cycle.setStatus(BillingCycle.BillingStatus.FAILED);
            billingCycleRepository.save(cycle);
            log.warn("Billing cycle={} FAILED (declined) for subscription={}: {}",
                    cycle.getId(), subscriptionId, ex.getDeclineReason());
            throw ex;

        } catch (PaymentGatewayException ex) {
            // Transient error — do NOT update the cycle status; let the transaction roll back
            // so Pub/Sub can nack and retry.
            log.error("Transient gateway error for billing cycle={} subscription={}",
                    cycle.getId(), subscriptionId, ex);
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private BillingCycle findOrThrow(Long id) {
        return billingCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", "id", id));
    }
}

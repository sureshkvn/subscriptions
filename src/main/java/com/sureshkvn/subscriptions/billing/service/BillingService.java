package com.sureshkvn.subscriptions.billing.service;

import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
import com.sureshkvn.subscriptions.billing.messaging.BillingDueEvent;
import com.sureshkvn.subscriptions.billing.model.BillingCycle;

import java.util.List;

/**
 * Service interface for billing cycle retrieval and management.
 */
public interface BillingService {

    List<BillingCycleResponse> getBillingCyclesBySubscription(Long subscriptionId);

    BillingCycleResponse getBillingCycleById(Long id);

    List<BillingCycleResponse> getBillingCyclesByStatus(BillingCycle.BillingStatus status);

    BillingCycleResponse markAsPaid(Long id, String paymentReference);

    BillingCycleResponse voidBillingCycle(Long id);

    /**
     * Processes a billing-due event received from Pub/Sub.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Idempotency check — if a {@link BillingCycle} for this
     *       {@code (subscriptionId, periodStart)} already exists, return immediately.</li>
     *   <li>Create a {@code PENDING} {@link BillingCycle} and flush to the database
     *       (idempotency guard via unique constraint).</li>
     *   <li>Call the payment gateway.</li>
     *   <li>On success: mark cycle {@code PAID}, advance {@code subscription.currentPeriodEnd}.</li>
     *   <li>On permanent decline: mark cycle {@code FAILED}; caller acks the message.</li>
     *   <li>On transient error: leave cycle {@code PENDING}; caller nacks for retry.</li>
     * </ol>
     *
     * @param event the billing-due event containing subscription ID and period boundaries
     */
    void processBillingCycle(BillingDueEvent event);
}

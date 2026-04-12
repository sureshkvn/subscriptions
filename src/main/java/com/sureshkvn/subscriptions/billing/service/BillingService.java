package com.sureshkvn.subscriptions.billing.service;

import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
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
}

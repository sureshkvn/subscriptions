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
}

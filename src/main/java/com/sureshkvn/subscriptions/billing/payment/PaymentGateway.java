package com.sureshkvn.subscriptions.billing.payment;

import com.sureshkvn.subscriptions.billing.model.BillingCycle;

/**
 * Abstraction over an external payment processor (e.g. Stripe, Braintree).
 *
 * <p>Implementations must distinguish between two failure modes:
 * <ul>
 *   <li><strong>Permanent decline</strong> — throw {@link PaymentDeclinedException}.
 *       The billing cycle is marked {@code FAILED} and the subscription enters dunning.
 *       The Pub/Sub message is <em>acked</em> to prevent infinite retry.</li>
 *   <li><strong>Transient error</strong> — throw {@link PaymentGatewayException}.
 *       The billing cycle remains {@code PENDING} and the Pub/Sub message is
 *       <em>nacked</em> so delivery is retried with exponential back-off.</li>
 * </ul>
 */
public interface PaymentGateway {

    /**
     * Charges the customer for the given billing cycle.
     *
     * @param cycle the billing cycle to charge (contains amount, currency, subscription reference)
     * @return a {@link PaymentResult} with a processor reference on success
     * @throws PaymentDeclinedException if the payment method permanently refuses the charge
     * @throws PaymentGatewayException  if the gateway is temporarily unavailable
     */
    PaymentResult charge(BillingCycle cycle);
}

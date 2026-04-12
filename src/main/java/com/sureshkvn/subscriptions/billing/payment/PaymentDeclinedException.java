package com.sureshkvn.subscriptions.billing.payment;

/**
 * Thrown when the payment gateway permanently declines a charge.
 *
 * <p>A decline is a <em>permanent</em> failure — the customer's payment method is definitively
 * rejected (e.g. card expired, insufficient funds, blocked). The billing cycle should be marked
 * {@code FAILED} and <strong>not retried</strong> automatically; the subscription should enter
 * a dunning workflow instead.
 *
 * <p>Contrast with {@link PaymentGatewayException}, which represents a <em>transient</em>
 * infrastructure error that should be retried via Pub/Sub back-off.
 */
public class PaymentDeclinedException extends RuntimeException {

    private final String declineReason;

    public PaymentDeclinedException(String declineReason) {
        super("Payment declined: " + declineReason);
        this.declineReason = declineReason;
    }

    public String getDeclineReason() {
        return declineReason;
    }
}

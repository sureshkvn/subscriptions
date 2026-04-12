package com.sureshkvn.subscriptions.billing.payment;

/**
 * Thrown when the payment gateway returns a transient / infrastructure error.
 *
 * <p>A gateway exception represents a <em>transient</em> failure — the payment provider was
 * unreachable, returned a 5xx, or timed out. The billing cycle should be left in {@code PENDING}
 * and the Pub/Sub message should be <strong>nacked</strong> so that the delivery is retried
 * with exponential back-off.
 *
 * <p>Contrast with {@link PaymentDeclinedException}, which represents a permanent card decline
 * that should <em>not</em> be automatically retried.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

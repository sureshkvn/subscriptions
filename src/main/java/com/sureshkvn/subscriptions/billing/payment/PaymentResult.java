package com.sureshkvn.subscriptions.billing.payment;

/**
 * Immutable result of a payment gateway charge attempt.
 *
 * @param success          {@code true} when the payment was accepted by the gateway.
 * @param paymentReference Processor-assigned reference ID (e.g. Stripe charge ID).
 *                         Non-null when {@code success} is {@code true}.
 * @param failureReason    Human-readable decline reason. Non-null when {@code success}
 *                         is {@code false}.
 */
public record PaymentResult(
        boolean success,
        String paymentReference,
        String failureReason
) {

    /** Convenience factory for a successful charge. */
    public static PaymentResult success(String reference) {
        return new PaymentResult(true, reference, null);
    }

    /** Convenience factory for a permanent decline (e.g. card declined, insufficient funds). */
    public static PaymentResult declined(String reason) {
        return new PaymentResult(false, null, reason);
    }
}

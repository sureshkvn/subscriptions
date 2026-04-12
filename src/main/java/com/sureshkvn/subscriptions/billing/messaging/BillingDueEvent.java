package com.sureshkvn.subscriptions.billing.messaging;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Pub/Sub event payload published by {@link com.sureshkvn.subscriptions.billing.job.BillingDispatcherJob}
 * for each subscription that is due for renewal.
 *
 * <p>The event carries the minimum information the processor needs so that it can
 * look up the subscription inside its own transaction rather than deserialising
 * stale snapshot data.
 *
 * @param subscriptionId  Primary key of the subscription to bill.
 * @param periodStart     Start of the billing period being invoiced.
 * @param periodEnd       End of the billing period being invoiced.
 * @param billingInterval Plain-string interval label for observability (e.g. {@code MONTHLY}).
 * @param publishedAt     Wall-clock time the event was created — useful for latency metrics.
 */
public record BillingDueEvent(
        Long subscriptionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant periodStart,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant periodEnd,
        String billingInterval,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant publishedAt
) {

    /** Convenience factory. */
    public static BillingDueEvent of(Long subscriptionId, Instant periodStart, Instant periodEnd,
                                     String billingInterval) {
        return new BillingDueEvent(subscriptionId, periodStart, periodEnd,
                billingInterval, Instant.now());
    }
}

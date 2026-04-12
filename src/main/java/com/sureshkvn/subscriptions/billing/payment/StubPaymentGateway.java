package com.sureshkvn.subscriptions.billing.payment;

import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * No-op {@link PaymentGateway} implementation for local development and testing.
 *
 * <p>Active only under the {@code !prod} profiles. Always returns a successful result with a
 * randomly generated reference, so the full billing flow can be exercised without a real
 * payment processor.
 *
 * <p>To simulate a decline in tests, subclass and override {@link #charge}.
 */
@Slf4j
@Component
@Profile("!prod")
public class StubPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(BillingCycle cycle) {
        String ref = "stub-" + UUID.randomUUID();
        log.info("[STUB] Charging subscription={} amount={} {} → ref={}",
                cycle.getSubscription().getId(),
                cycle.getAmount(),
                cycle.getCurrency(),
                ref);
        return PaymentResult.success(ref);
    }
}

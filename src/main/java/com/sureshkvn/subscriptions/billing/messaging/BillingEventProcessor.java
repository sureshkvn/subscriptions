package com.sureshkvn.subscriptions.billing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.billing.payment.PaymentDeclinedException;
import com.sureshkvn.subscriptions.billing.payment.PaymentGatewayException;
import com.sureshkvn.subscriptions.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Internal HTTP endpoint that receives Pub/Sub push deliveries for billing events.
 *
 * <h2>Pub/Sub push contract</h2>
 * <ul>
 *   <li>Return <strong>2xx</strong> → Pub/Sub <em>acks</em> the message (processed or
 *       permanently failed — won't retry).</li>
 *   <li>Return <strong>5xx / timeout</strong> → Pub/Sub <em>nacks</em> the message and
 *       retries with exponential back-off up to the subscription's
 *       {@code maxDeliveryAttempts}.</li>
 * </ul>
 *
 * <h2>Failure classification</h2>
 * <ul>
 *   <li>{@link PaymentDeclinedException} — permanent decline: ack (200) and let
 *       {@link BillingService} mark the cycle {@code FAILED}.</li>
 *   <li>{@link PaymentGatewayException} — transient error: return 500 so Pub/Sub retries.</li>
 *   <li>Any other exception — unknown cause: return 500 to trigger retry.</li>
 * </ul>
 *
 * <p>This endpoint is intentionally <em>not</em> secured by OAuth — it lives under
 * {@code /internal/} and must be shielded by a Cloud Run Ingress / Load Balancer rule that
 * allows traffic only from the Pub/Sub service account's IP range.
 */
@Slf4j
@RestController
@RequestMapping("/internal/billing")
@RequiredArgsConstructor
public class BillingEventProcessor {

    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    /**
     * Receives a Pub/Sub push message, decodes the {@link BillingDueEvent} payload,
     * and delegates processing to {@link BillingService#processBillingCycle}.
     */
    @PostMapping("/process")
    public ResponseEntity<Void> process(@RequestBody PubSubPushEnvelope envelope) {
        BillingDueEvent event = decode(envelope);
        log.info("Received billing event subscriptionId={} period={}/{}",
                event.subscriptionId(), event.periodStart(), event.periodEnd());

        try {
            billingService.processBillingCycle(event);
            return ResponseEntity.ok().build();

        } catch (PaymentDeclinedException ex) {
            // Permanent decline — ack the message; BillingService already marked cycle FAILED.
            log.warn("Payment declined for subscription={}: {}", event.subscriptionId(),
                    ex.getDeclineReason());
            return ResponseEntity.ok().build();

        } catch (PaymentGatewayException ex) {
            // Transient gateway error — nack by returning 500 so Pub/Sub retries.
            log.error("Transient payment gateway error for subscription={}: {}",
                    event.subscriptionId(), ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();

        } catch (Exception ex) {
            // Unknown — nack and let Pub/Sub retry.
            log.error("Unexpected error processing billing event for subscription={}",
                    event.subscriptionId(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BillingDueEvent decode(PubSubPushEnvelope envelope) {
        try {
            byte[] decoded = Base64.getDecoder().decode(envelope.message().data());
            return objectMapper.readValue(decoded, BillingDueEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not decode Pub/Sub message payload", e);
        }
    }
}

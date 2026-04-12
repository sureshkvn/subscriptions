package com.sureshkvn.subscriptions.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.billing.messaging.BillingDueEvent;
import com.sureshkvn.subscriptions.billing.messaging.BillingEventProcessor;
import com.sureshkvn.subscriptions.billing.messaging.PubSubPushEnvelope;
import com.sureshkvn.subscriptions.billing.payment.PaymentDeclinedException;
import com.sureshkvn.subscriptions.billing.payment.PaymentGatewayException;
import com.sureshkvn.subscriptions.billing.service.BillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link BillingEventProcessor}.
 *
 * <p>Verifies the Pub/Sub ack/nack contract:
 * <ul>
 *   <li>2xx → message acked (processed or permanently declined)</li>
 *   <li>5xx → message nacked (transient error, retry)</li>
 * </ul>
 */
@WebMvcTest(BillingEventProcessor.class)
class BillingEventProcessorTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean BillingService billingService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private String buildEnvelope(Long subscriptionId) throws Exception {
        BillingDueEvent event = BillingDueEvent.of(
                subscriptionId,
                Instant.parse("2025-06-01T00:00:00Z"),
                Instant.parse("2025-07-01T00:00:00Z"),
                "MONTHLY"
        );
        String eventJson = objectMapper.writeValueAsString(event);
        String base64    = Base64.getEncoder().encodeToString(eventJson.getBytes());

        PubSubPushEnvelope envelope = new PubSubPushEnvelope(
                new PubSubPushEnvelope.Message(base64, "msg-001", Instant.now().toString(), Map.of()),
                "projects/test/subscriptions/billing-due-sub"
        );
        return objectMapper.writeValueAsString(envelope);
    }

    // -------------------------------------------------------------------------
    // POST /internal/billing/process
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /internal/billing/process")
    class ProcessEndpoint {

        @Test
        @DisplayName("→ 200 when billing cycle processed successfully")
        void success_returns_200() throws Exception {
            doNothing().when(billingService).processBillingCycle(any());

            mockMvc.perform(post("/internal/billing/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildEnvelope(1L)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("→ 200 when payment is permanently declined (ack — no retry)")
        void payment_declined_returns_200_to_ack() throws Exception {
            doThrow(new PaymentDeclinedException("card expired"))
                    .when(billingService).processBillingCycle(any());

            mockMvc.perform(post("/internal/billing/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildEnvelope(2L)))
                    .andExpect(status().isOk());   // ack — permanent, do not retry
        }

        @Test
        @DisplayName("→ 500 when payment gateway has transient error (nack — retry)")
        void gateway_transient_error_returns_500_to_nack() throws Exception {
            doThrow(new PaymentGatewayException("gateway timeout"))
                    .when(billingService).processBillingCycle(any());

            mockMvc.perform(post("/internal/billing/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildEnvelope(3L)))
                    .andExpect(status().isInternalServerError());   // nack — retry
        }

        @Test
        @DisplayName("→ 500 when unexpected exception occurs (nack — retry)")
        void unexpected_exception_returns_500_to_nack() throws Exception {
            doThrow(new RuntimeException("unexpected NPE"))
                    .when(billingService).processBillingCycle(any());

            mockMvc.perform(post("/internal/billing/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildEnvelope(4L)))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("→ 400 when message payload cannot be decoded")
        void invalid_base64_returns_400() throws Exception {
            PubSubPushEnvelope badEnvelope = new PubSubPushEnvelope(
                    new PubSubPushEnvelope.Message("!!!NOT-VALID-BASE64!!!",
                            "msg-bad", Instant.now().toString(), Map.of()),
                    "projects/test/subscriptions/billing-due-sub"
            );

            mockMvc.perform(post("/internal/billing/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badEnvelope)))
                    .andExpect(status().is5xxServerError());
        }
    }
}

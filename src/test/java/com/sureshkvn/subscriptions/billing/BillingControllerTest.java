package com.sureshkvn.subscriptions.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.billing.controller.BillingController;
import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import com.sureshkvn.subscriptions.billing.service.BillingService;
import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link BillingController} — verifies HTTP layer behavior without
 * starting the full application context.
 */
@WebMvcTest(BillingController.class)
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BillingService billingService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private BillingCycleResponse buildCycleResponse(Long id, BillingCycle.BillingStatus status) {
        return new BillingCycleResponse(
                id,
                1L,
                new BigDecimal("29.99"),
                "USD",
                Instant.now(),
                Instant.now().plusSeconds(2_592_000L),
                status,
                status == BillingCycle.BillingStatus.PAID ? Instant.now() : null,
                status == BillingCycle.BillingStatus.PAID ? "ch_stripe_abc123" : null,
                Instant.now()
        );
    }

    // -------------------------------------------------------------------------
    // GET /v1/billing/subscriptions/{subscriptionId}/cycles
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/billing/subscriptions/{id}/cycles → 200 with list of cycles")
    void getBillingCycles_existingSubscription_returns200() throws Exception {
        List<BillingCycleResponse> cycles = List.of(
                buildCycleResponse(1L, BillingCycle.BillingStatus.PAID),
                buildCycleResponse(2L, BillingCycle.BillingStatus.PENDING)
        );
        when(billingService.getBillingCyclesBySubscription(1L)).thenReturn(cycles);

        mockMvc.perform(get("/v1/billing/subscriptions/1/cycles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].subscriptionId").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PAID"))
                .andExpect(jsonPath("$.data[1].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /v1/billing/subscriptions/{id}/cycles → 200 with empty list")
    void getBillingCycles_noHistory_returnsEmptyList() throws Exception {
        when(billingService.getBillingCyclesBySubscription(99L)).thenReturn(List.of());

        mockMvc.perform(get("/v1/billing/subscriptions/99/cycles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // -------------------------------------------------------------------------
    // GET /v1/billing/cycles/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/billing/cycles/{id} → 200 for existing cycle")
    void getBillingCycleById_existingId_returns200() throws Exception {
        when(billingService.getBillingCycleById(1L))
                .thenReturn(buildCycleResponse(1L, BillingCycle.BillingStatus.PENDING));

        mockMvc.perform(get("/v1/billing/cycles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.amount").value(29.99))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    @DisplayName("GET /v1/billing/cycles/{id} → 404 when cycle not found")
    void getBillingCycleById_notFound_returns404() throws Exception {
        when(billingService.getBillingCycleById(999L))
                .thenThrow(new ResourceNotFoundException("BillingCycle", "id", 999L));

        mockMvc.perform(get("/v1/billing/cycles/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("BillingCycle not found with id: '999'"));
    }

    // -------------------------------------------------------------------------
    // GET /v1/billing/cycles?status=PENDING
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/billing/cycles?status=PENDING → 200 with filtered list")
    void getBillingCyclesByStatus_pendingFilter_returns200() throws Exception {
        when(billingService.getBillingCyclesByStatus(BillingCycle.BillingStatus.PENDING))
                .thenReturn(List.of(buildCycleResponse(2L, BillingCycle.BillingStatus.PENDING)));

        mockMvc.perform(get("/v1/billing/cycles").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    // -------------------------------------------------------------------------
    // PATCH /v1/billing/cycles/{id}/pay
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /v1/billing/cycles/{id}/pay → 200 when marking as paid")
    void markAsPaid_pendingCycle_returns200() throws Exception {
        when(billingService.markAsPaid(1L, "ch_stripe_abc123"))
                .thenReturn(buildCycleResponse(1L, BillingCycle.BillingStatus.PAID));

        mockMvc.perform(patch("/v1/billing/cycles/1/pay")
                        .param("paymentReference", "ch_stripe_abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Billing cycle marked as paid"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentReference").value("ch_stripe_abc123"));
    }

    @Test
    @DisplayName("PATCH /v1/billing/cycles/{id}/pay → 422 when cycle is not PENDING")
    void markAsPaid_alreadyPaidCycle_returns422() throws Exception {
        when(billingService.markAsPaid(1L, "ch_duplicate"))
                .thenThrow(new BusinessRuleException(
                        "Only PENDING billing cycles can be marked as paid (current: PAID)"));

        mockMvc.perform(patch("/v1/billing/cycles/1/pay")
                        .param("paymentReference", "ch_duplicate"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Only PENDING billing cycles can be marked as paid (current: PAID)"));
    }

    // -------------------------------------------------------------------------
    // PATCH /v1/billing/cycles/{id}/void
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /v1/billing/cycles/{id}/void → 200 when voiding a PENDING cycle")
    void voidCycle_pendingCycle_returns200() throws Exception {
        when(billingService.voidBillingCycle(1L))
                .thenReturn(buildCycleResponse(1L, BillingCycle.BillingStatus.VOID));

        mockMvc.perform(patch("/v1/billing/cycles/1/void"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Billing cycle voided"))
                .andExpect(jsonPath("$.data.status").value("VOID"));
    }

    @Test
    @DisplayName("PATCH /v1/billing/cycles/{id}/void → 422 when cycle is already PAID")
    void voidCycle_paidCycle_returns422() throws Exception {
        when(billingService.voidBillingCycle(1L))
                .thenThrow(new BusinessRuleException(
                        "Cannot void a PAID billing cycle. Use refund instead."));

        mockMvc.perform(patch("/v1/billing/cycles/1/void"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /v1/billing/cycles/{id}/void → 404 when cycle not found")
    void voidCycle_notFound_returns404() throws Exception {
        when(billingService.voidBillingCycle(999L))
                .thenThrow(new ResourceNotFoundException("BillingCycle", "id", 999L));

        mockMvc.perform(patch("/v1/billing/cycles/999/void"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

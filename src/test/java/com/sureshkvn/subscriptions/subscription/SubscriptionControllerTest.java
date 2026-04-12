package com.sureshkvn.subscriptions.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.subscription.controller.SubscriptionController;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link SubscriptionController}.
 */
@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionService subscriptionService;

    private SubscriptionResponse buildSubscriptionResponse(Long id) {
        PlanResponse plan = new PlanResponse(
                1L, "Pro Monthly", "Pro plan",
                new BigDecimal("29.99"), "USD",
                Plan.BillingInterval.MONTHLY, 1, 0,
                Plan.PlanStatus.ACTIVE, Instant.now(), Instant.now());

        return new SubscriptionResponse(
                id, "customer-123", plan,
                Subscription.SubscriptionStatus.ACTIVE,
                Instant.now(), Instant.now().plusSeconds(2592000L),
                null, null, null, null,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("POST /v1/subscriptions → 201 Created with valid request")
    void createSubscription_validRequest_returns201() throws Exception {
        SubscriptionRequest request = SubscriptionRequest.builder()
                .customerId("customer-123")
                .planId(1L)
                .build();

        when(subscriptionService.createSubscription(any())).thenReturn(buildSubscriptionResponse(1L));

        mockMvc.perform(post("/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").value("customer-123"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /v1/subscriptions → 400 when customerId is missing")
    void createSubscription_missingCustomerId_returns400() throws Exception {
        SubscriptionRequest invalid = SubscriptionRequest.builder()
                .planId(1L)
                .build();

        mockMvc.perform(post("/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/subscriptions/{id} → 200 OK")
    void getSubscriptionById_returns200() throws Exception {
        when(subscriptionService.getSubscriptionById(1L)).thenReturn(buildSubscriptionResponse(1L));

        mockMvc.perform(get("/v1/subscriptions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}

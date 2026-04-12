package com.sureshkvn.subscriptions.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.plan.controller.PlanController;
import com.sureshkvn.subscriptions.plan.dto.PlanRequest;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.plan.service.PlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link PlanController} — verifies HTTP layer behavior without
 * starting the full application context.
 */
@WebMvcTest(PlanController.class)
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlanService planService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private PlanRequest buildValidRequest() {
        return PlanRequest.builder()
                .name("Pro Monthly")
                .description("Pro plan billed monthly")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(Plan.BillingInterval.MONTHLY)
                .intervalCount(1)
                .trialPeriodDays(14)
                .build();
    }

    private PlanResponse buildPlanResponse(Long id) {
        return new PlanResponse(
                id, "Pro Monthly", "Pro plan billed monthly",
                new BigDecimal("29.99"), "USD",
                Plan.BillingInterval.MONTHLY, 1, 14,
                Plan.PlanStatus.ACTIVE, Instant.now(), Instant.now());
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/plans → 201 Created with valid request")
    void createPlan_validRequest_returns201() throws Exception {
        PlanRequest request = buildValidRequest();
        PlanResponse response = buildPlanResponse(1L);

        when(planService.createPlan(any(PlanRequest.class))).thenReturn(response);

        mockMvc.perform(post("/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Pro Monthly"))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    @DisplayName("POST /v1/plans → 400 Bad Request when name is blank")
    void createPlan_blankName_returns400() throws Exception {
        PlanRequest invalid = PlanRequest.builder()
                .name("")
                .price(new BigDecimal("9.99"))
                .currency("USD")
                .billingInterval(Plan.BillingInterval.MONTHLY)
                .build();

        mockMvc.perform(post("/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /v1/plans → 200 OK with list of plans")
    void getAllPlans_returns200() throws Exception {
        when(planService.getAllPlans()).thenReturn(List.of(buildPlanResponse(1L)));

        mockMvc.perform(get("/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /v1/plans/{id} → 200 OK for existing plan")
    void getPlanById_existingId_returns200() throws Exception {
        when(planService.getPlanById(1L)).thenReturn(buildPlanResponse(1L));

        mockMvc.perform(get("/v1/plans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}

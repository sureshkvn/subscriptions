package com.sureshkvn.subscriptions.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.subscription.controller.SubscriptionController;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionRequest;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link SubscriptionController}.
 */
@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @MockitoBean SubscriptionService subscriptionService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private SubscriptionResponse buildResponse(Long id, BigDecimal totalDiscount) {
        PlanResponse plan = new PlanResponse(
                1L, "Pro Monthly", "Pro plan",
                new BigDecimal("100.00"), "USD",
                Plan.BillingInterval.MONTHLY, 1, 0,
                Plan.PlanStatus.ACTIVE, Instant.now(), Instant.now());

        BigDecimal effectivePrice = new BigDecimal("100.00").subtract(totalDiscount)
                .max(BigDecimal.ZERO);

        return SubscriptionResponse.builder()
                .id(id)
                .customerId("customer-123")
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(2_592_000L))
                .appliedCoupons(List.of())
                .totalDiscountAmount(totalDiscount)
                .effectivePrice(effectivePrice)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /v1/subscriptions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /v1/subscriptions")
    class CreateSubscription {

        @Test
        @DisplayName("→ 201 without coupons")
        void no_coupons_returns_201() throws Exception {
            SubscriptionRequest request = SubscriptionRequest.builder()
                    .customerId("customer-123")
                    .planId(1L)
                    .build();

            when(subscriptionService.createSubscription(any()))
                    .thenReturn(buildResponse(1L, BigDecimal.ZERO));

            mockMvc.perform(post("/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.customerId").value("customer-123"))
                    .andExpect(jsonPath("$.data.totalDiscountAmount").value(0))
                    .andExpect(jsonPath("$.data.effectivePrice").value(100.00));
        }

        @Test
        @DisplayName("→ 201 with two stacked coupons applied (20% + $15 on $100 = $65)")
        void two_stacked_coupons_returns_201() throws Exception {
            SubscriptionRequest request = SubscriptionRequest.builder()
                    .customerId("customer-123")
                    .planId(1L)
                    .couponCodes(List.of("SUMMER20", "FLAT15"))
                    .build();

            when(subscriptionService.createSubscription(any()))
                    .thenReturn(buildResponse(1L, new BigDecimal("35.00")));

            mockMvc.perform(post("/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.totalDiscountAmount").value(35.00))
                    .andExpect(jsonPath("$.data.effectivePrice").value(65.00));
        }

        @Test
        @DisplayName("→ 422 when non-stackable coupon is combined with another")
        void non_stackable_combined_returns_422() throws Exception {
            SubscriptionRequest request = SubscriptionRequest.builder()
                    .customerId("customer-123")
                    .planId(1L)
                    .couponCodes(List.of("EXCLUSIVE", "SUMMER20"))
                    .build();

            when(subscriptionService.createSubscription(any()))
                    .thenThrow(new BusinessRuleException(
                            "Coupon(s) [EXCLUSIVE] are non-stackable and cannot be combined"));

            mockMvc.perform(post("/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("→ 400 when customerId is missing")
        void missing_customer_id_returns_400() throws Exception {
            SubscriptionRequest invalid = SubscriptionRequest.builder().planId(1L).build();

            mockMvc.perform(post("/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // GET /v1/subscriptions/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /{id} → 200 OK")
    void getById_returns_200() throws Exception {
        when(subscriptionService.getSubscriptionById(1L))
                .thenReturn(buildResponse(1L, BigDecimal.ZERO));

        mockMvc.perform(get("/v1/subscriptions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // -------------------------------------------------------------------------
    // Coupon endpoints
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /v1/subscriptions/{id}/coupons")
    class AddCoupon {

        @Test
        @DisplayName("→ 200 when stackable coupon added to subscription with existing coupons")
        void add_stackable_coupon_returns_200() throws Exception {
            when(subscriptionService.addCoupon(1L, "FLAT15"))
                    .thenReturn(buildResponse(1L, new BigDecimal("15.00")));

            mockMvc.perform(post("/v1/subscriptions/1/coupons")
                            .param("couponCode", "FLAT15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalDiscountAmount").value(15.00))
                    .andExpect(jsonPath("$.message").value("Coupon 'FLAT15' applied successfully"));
        }

        @Test
        @DisplayName("→ 422 when adding non-stackable coupon to subscription with existing coupons")
        void add_non_stackable_to_existing_returns_422() throws Exception {
            when(subscriptionService.addCoupon(1L, "EXCLUSIVE"))
                    .thenThrow(new BusinessRuleException(
                            "Coupon 'EXCLUSIVE' is non-stackable and cannot be added "
                                    + "to a subscription that already has other coupons applied"));

            mockMvc.perform(post("/v1/subscriptions/1/coupons")
                            .param("couponCode", "EXCLUSIVE"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("→ 422 when adding duplicate coupon code")
        void add_duplicate_coupon_returns_422() throws Exception {
            when(subscriptionService.addCoupon(1L, "SUMMER20"))
                    .thenThrow(new BusinessRuleException(
                            "Coupon 'SUMMER20' is already applied to this subscription"));

            mockMvc.perform(post("/v1/subscriptions/1/coupons")
                            .param("couponCode", "SUMMER20"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("DELETE /v1/subscriptions/{id}/coupons/{code}")
    class RemoveCoupon {

        @Test
        @DisplayName("→ 200 when revoking an active coupon")
        void revoke_active_coupon_returns_200() throws Exception {
            when(subscriptionService.removeCoupon(eq(1L), any()))
                    .thenReturn(buildResponse(1L, BigDecimal.ZERO));

            mockMvc.perform(delete("/v1/subscriptions/1/coupons/SUMMER20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Coupon 'SUMMER20' revoked successfully"));
        }
    }
}

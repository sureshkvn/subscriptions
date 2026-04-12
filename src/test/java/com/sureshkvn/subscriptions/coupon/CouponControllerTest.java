package com.sureshkvn.subscriptions.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import com.sureshkvn.subscriptions.coupon.controller.CouponController;
import com.sureshkvn.subscriptions.coupon.dto.CouponRequest;
import com.sureshkvn.subscriptions.coupon.dto.CouponResponse;
import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.service.CouponService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link CouponController}.
 */
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @MockitoBean CouponService couponService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private CouponRequest buildPercentRequest(boolean stackable) {
        return CouponRequest.builder()
                .code("SUMMER20")
                .description("20% off")
                .discountType(Coupon.DiscountType.PERCENT)
                .discountValue(new BigDecimal("20"))
                .stackable(stackable)
                .build();
    }

    private CouponResponse buildPercentResponse(boolean stackable) {
        return new CouponResponse(
                1L, "SUMMER20", "20% off",
                Coupon.DiscountType.PERCENT, new BigDecimal("20"),
                stackable, null, null, 0, null,
                Coupon.CouponStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private CouponResponse buildFixedResponse() {
        return new CouponResponse(
                2L, "FLAT15", "15$ off",
                Coupon.DiscountType.FIXED_AMOUNT, new BigDecimal("15"),
                true, null, 100, 3, null,
                Coupon.CouponStatus.ACTIVE, Instant.now(), Instant.now());
    }

    // -------------------------------------------------------------------------
    // POST /v1/coupons
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /v1/coupons")
    class CreateCoupon {

        @Test
        @DisplayName("→ 201 with valid stackable percent coupon")
        void valid_stackable_percent() throws Exception {
            when(couponService.createCoupon(any())).thenReturn(buildPercentResponse(true));

            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPercentRequest(true))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value("SUMMER20"))
                    .andExpect(jsonPath("$.data.discountType").value("PERCENT"))
                    .andExpect(jsonPath("$.data.stackable").value(true));
        }

        @Test
        @DisplayName("→ 201 with valid non-stackable coupon")
        void valid_non_stackable() throws Exception {
            when(couponService.createCoupon(any())).thenReturn(buildPercentResponse(false));

            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPercentRequest(false))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.stackable").value(false));
        }

        @Test
        @DisplayName("→ 400 when code is blank")
        void blank_code_returns_400() throws Exception {
            CouponRequest invalid = CouponRequest.builder()
                    .code("")
                    .discountType(Coupon.DiscountType.PERCENT)
                    .discountValue(new BigDecimal("10"))
                    .build();

            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("→ 400 when discount value is zero")
        void zero_discount_returns_400() throws Exception {
            CouponRequest invalid = CouponRequest.builder()
                    .code("ZERODISCOUNT")
                    .discountType(Coupon.DiscountType.PERCENT)
                    .discountValue(BigDecimal.ZERO)
                    .build();

            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("→ 422 when duplicate code is submitted")
        void duplicate_code_returns_422() throws Exception {
            when(couponService.createCoupon(any()))
                    .thenThrow(new BusinessRuleException("A coupon with code 'SUMMER20' already exists"));

            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPercentRequest(true))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("A coupon with code 'SUMMER20' already exists"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /v1/coupons/{code}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /v1/coupons/{code}")
    class GetByCode {

        @Test
        @DisplayName("→ 200 for existing code")
        void existing_code_returns_200() throws Exception {
            when(couponService.getCouponByCode("SUMMER20")).thenReturn(buildPercentResponse(true));

            mockMvc.perform(get("/v1/coupons/SUMMER20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").value("SUMMER20"));
        }

        @Test
        @DisplayName("→ 404 for unknown code")
        void unknown_code_returns_404() throws Exception {
            when(couponService.getCouponByCode("UNKNOWN"))
                    .thenThrow(new ResourceNotFoundException("Coupon", "code", "UNKNOWN"));

            mockMvc.perform(get("/v1/coupons/UNKNOWN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // -------------------------------------------------------------------------
    // GET /v1/coupons
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /v1/coupons")
    class GetAll {

        @Test
        @DisplayName("→ 200 with all coupons")
        void returns_all_coupons() throws Exception {
            when(couponService.getAllCoupons())
                    .thenReturn(List.of(buildPercentResponse(true), buildFixedResponse()));

            mockMvc.perform(get("/v1/coupons"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("→ 200 filtered by ACTIVE status")
        void filtered_by_status() throws Exception {
            when(couponService.getCouponsByStatus(Coupon.CouponStatus.ACTIVE))
                    .thenReturn(List.of(buildPercentResponse(true)));

            mockMvc.perform(get("/v1/coupons").param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
        }
    }

    // -------------------------------------------------------------------------
    // PATCH /v1/coupons/{code}/deactivate
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /v1/coupons/{code}/deactivate")
    class Deactivate {

        @Test
        @DisplayName("→ 200 when deactivating an active coupon")
        void active_coupon_deactivated() throws Exception {
            CouponResponse inactive = new CouponResponse(
                    1L, "SUMMER20", "20% off",
                    Coupon.DiscountType.PERCENT, new BigDecimal("20"),
                    true, null, null, 0, null,
                    Coupon.CouponStatus.INACTIVE, Instant.now(), Instant.now());

            when(couponService.deactivateCoupon("SUMMER20")).thenReturn(inactive);

            mockMvc.perform(patch("/v1/coupons/SUMMER20/deactivate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                    .andExpect(jsonPath("$.message").value("Coupon deactivated successfully"));
        }

        @Test
        @DisplayName("→ 422 when coupon is already inactive")
        void already_inactive_returns_422() throws Exception {
            when(couponService.deactivateCoupon("SUMMER20"))
                    .thenThrow(new BusinessRuleException("Coupon 'SUMMER20' is already inactive"));

            mockMvc.perform(patch("/v1/coupons/SUMMER20/deactivate"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("→ 404 for unknown code")
        void unknown_code_returns_404() throws Exception {
            when(couponService.deactivateCoupon("GHOST"))
                    .thenThrow(new ResourceNotFoundException("Coupon", "code", "GHOST"));

            mockMvc.perform(patch("/v1/coupons/GHOST/deactivate"))
                    .andExpect(status().isNotFound());
        }
    }
}

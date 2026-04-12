package com.sureshkvn.subscriptions.coupon;

import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.service.DiscountCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link DiscountCalculator}.
 *
 * <p>No Spring context — tests the discount math in complete isolation.
 * Each scenario documents the expected parallel-strategy behaviour and the
 * floor rule (final price ≥ 0.00).
 */
class DiscountCalculatorTest {

    private DiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DiscountCalculator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Coupon percentCoupon(double pct) {
        return Coupon.builder()
                .code("PCT" + (int) pct)
                .discountType(Coupon.DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(pct))
                .stackable(true)
                .build();
    }

    private Coupon fixedCoupon(double amount) {
        return Coupon.builder()
                .code("FIX" + (int) amount)
                .discountType(Coupon.DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(amount))
                .stackable(true)
                .build();
    }

    private static final BigDecimal BASE = new BigDecimal("100.00");

    // -------------------------------------------------------------------------
    // Single coupon
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Single coupon")
    class SingleCoupon {

        @Test
        @DisplayName("PERCENT 20% on $100 → discount $20.00, final $80.00")
        void percent_20_on_100() {
            Coupon c = percentCoupon(20);
            assertThat(calculator.computeSingleDiscount(BASE, c))
                    .isEqualByComparingTo("20.00");
            assertThat(calculator.computeFinalAmount(BASE, List.of(c)))
                    .isEqualByComparingTo("80.00");
        }

        @Test
        @DisplayName("FIXED $15 on $100 → discount $15.00, final $85.00")
        void fixed_15_on_100() {
            Coupon c = fixedCoupon(15);
            assertThat(calculator.computeSingleDiscount(BASE, c))
                    .isEqualByComparingTo("15.00");
            assertThat(calculator.computeFinalAmount(BASE, List.of(c)))
                    .isEqualByComparingTo("85.00");
        }

        @Test
        @DisplayName("PERCENT 100% → final price is exactly $0.00")
        void percent_100_floors_to_zero() {
            Coupon c = percentCoupon(100);
            assertThat(calculator.computeFinalAmount(BASE, List.of(c)))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("FIXED greater than base ($150 on $100) → final price is $0.00")
        void fixed_exceeding_base_floors_to_zero() {
            Coupon c = fixedCoupon(150);
            assertThat(calculator.computeFinalAmount(BASE, List.of(c)))
                    .isEqualByComparingTo("0.00");
        }
    }

    // -------------------------------------------------------------------------
    // Stacked coupons — parallel strategy
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Stacked coupons — parallel strategy")
    class StackedCoupons {

        @Test
        @DisplayName("20% + 10% on $100 → total discount $30.00, final $70.00")
        void two_percent_coupons() {
            List<Coupon> coupons = List.of(percentCoupon(20), percentCoupon(10));
            assertThat(calculator.computeTotalDiscount(BASE, coupons))
                    .isEqualByComparingTo("30.00");
            assertThat(calculator.computeFinalAmount(BASE, coupons))
                    .isEqualByComparingTo("70.00");
        }

        @Test
        @DisplayName("20% + $15 fixed on $100 → total discount $35.00, final $65.00")
        void percent_and_fixed() {
            // PERCENT 20%: 100 × 0.20 = 20.00
            // FIXED  $15:                15.00
            // Total discount:            35.00  → final 65.00
            List<Coupon> coupons = List.of(percentCoupon(20), fixedCoupon(15));
            assertThat(calculator.computeTotalDiscount(BASE, coupons))
                    .isEqualByComparingTo("35.00");
            assertThat(calculator.computeFinalAmount(BASE, coupons))
                    .isEqualByComparingTo("65.00");
        }

        @Test
        @DisplayName("$60 + $60 on $100 → total discount capped at $100, final $0.00")
        void two_fixed_coupons_exceeding_base_floor() {
            // Each computed independently: $60 + $60 = $120 raw
            // Capped at base ($100) → final $0.00
            List<Coupon> coupons = List.of(fixedCoupon(60), fixedCoupon(60));
            assertThat(calculator.computeTotalDiscount(BASE, coupons))
                    .isEqualByComparingTo("100.00");   // capped, not 120
            assertThat(calculator.computeFinalAmount(BASE, coupons))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Order-independence: 20%+$15 == $15+20% (parallel is commutative)")
        void order_independence() {
            List<Coupon> order1 = List.of(percentCoupon(20), fixedCoupon(15));
            List<Coupon> order2 = List.of(fixedCoupon(15), percentCoupon(20));
            assertThat(calculator.computeFinalAmount(BASE, order1))
                    .isEqualByComparingTo(calculator.computeFinalAmount(BASE, order2));
        }

        @Test
        @DisplayName("Three coupons: 10% + $5 + 5% on $100 → discount $20.00, final $80.00")
        void three_coupons() {
            // 10%: $10.00 | $5 fixed: $5.00 | 5%: $5.00 → total $20.00
            List<Coupon> coupons = List.of(percentCoupon(10), fixedCoupon(5), percentCoupon(5));
            assertThat(calculator.computeTotalDiscount(BASE, coupons))
                    .isEqualByComparingTo("20.00");
            assertThat(calculator.computeFinalAmount(BASE, coupons))
                    .isEqualByComparingTo("80.00");
        }
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty coupon list → discount $0.00, final = base")
        void empty_list() {
            assertThat(calculator.computeTotalDiscount(BASE, List.of()))
                    .isEqualByComparingTo("0.00");
            assertThat(calculator.computeFinalAmount(BASE, List.of()))
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Null coupon list → discount $0.00, final = base")
        void null_list() {
            assertThat(calculator.computeTotalDiscount(BASE, null))
                    .isEqualByComparingTo("0.00");
            assertThat(calculator.computeFinalAmount(BASE, null))
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Zero base price + any coupon → final always $0.00")
        void zero_base_price() {
            BigDecimal zeroBase = BigDecimal.ZERO;
            List<Coupon> coupons = List.of(percentCoupon(20), fixedCoupon(10));
            assertThat(calculator.computeFinalAmount(zeroBase, coupons))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("PERCENT 0.5% on $100.00 → discount $0.50, final $99.50 (rounding half-up)")
        void fractional_percent_rounding() {
            Coupon c = percentCoupon(0.5);
            assertThat(calculator.computeSingleDiscount(BASE, c))
                    .isEqualByComparingTo("0.50");
            assertThat(calculator.computeFinalAmount(BASE, List.of(c)))
                    .isEqualByComparingTo("99.50");
        }
    }
}

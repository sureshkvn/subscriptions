package com.sureshkvn.subscriptions.coupon.service;

import com.sureshkvn.subscriptions.coupon.model.Coupon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Stateless component that computes coupon discounts using the <b>parallel strategy</b>.
 *
 * <h2>Parallel Strategy</h2>
 * <p>Each coupon's discount is computed independently against the original base amount.
 * Individual discount amounts are summed to produce the total discount.
 * The total is then capped so the final amount never falls below {@code 0.00} in
 * the subscription's currency.
 *
 * <pre>
 * Example — base price $100, two stackable coupons (20% + $15 fixed):
 *
 *   PERCENT  coupon: 100 × 20 / 100 = $20.00
 *   FIXED    coupon:                  $15.00
 *   ─────────────────────────────────────────
 *   Total discount:                   $35.00   (< base, no capping needed)
 *   Final amount:   100 - 35        = $65.00
 *
 * Example — base price $10, coupons sum to $30 discount:
 *   Total discount capped at base   = $10.00
 *   Final amount:                   = $0.00   ← floor enforced
 * </pre>
 *
 * <h2>Why parallel, not sequential?</h2>
 * <p>Order-independence: the customer sees "20% coupon + $15 coupon" and the result
 * is always the same regardless of which was applied first. Sequential application
 * would produce different results depending on application order, creating subtle bugs
 * and confusion in customer-facing receipts.
 */
@Component
public class DiscountCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Computes the total discount for a list of coupons applied in parallel against
     * {@code baseAmount}. The returned value is capped at {@code baseAmount}.
     *
     * @param baseAmount the plan's base price (must be ≥ 0)
     * @param coupons    the coupons to apply (may be empty)
     * @return total discount amount, in the range [0, baseAmount]
     */
    public BigDecimal computeTotalDiscount(BigDecimal baseAmount, List<Coupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = coupons.stream()
                .map(c -> computeSingleDiscount(baseAmount, c))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Cap total discount at baseAmount — ensures final price ≥ 0.00
        return total.min(baseAmount).setScale(SCALE, ROUNDING);
    }

    /**
     * Returns the final amount after applying all coupons.
     * Guaranteed to be ≥ {@link BigDecimal#ZERO}.
     *
     * @param baseAmount the plan's base price (must be ≥ 0)
     * @param coupons    the coupons to apply (may be empty)
     * @return final charged amount, always ≥ 0.00
     */
    public BigDecimal computeFinalAmount(BigDecimal baseAmount, List<Coupon> coupons) {
        BigDecimal discount = computeTotalDiscount(baseAmount, coupons);
        return baseAmount.subtract(discount)
                .max(BigDecimal.ZERO)        // explicit floor — never negative
                .setScale(SCALE, ROUNDING);
    }

    /**
     * Computes the discount for a single coupon against {@code baseAmount},
     * without any floor/ceiling capping. Capping is applied by the caller
     * after summing all individual discounts.
     *
     * @param baseAmount the plan's base price
     * @param coupon     the coupon to compute
     * @return this coupon's raw discount contribution
     */
    public BigDecimal computeSingleDiscount(BigDecimal baseAmount, Coupon coupon) {
        return switch (coupon.getDiscountType()) {
            case PERCENT ->
                    baseAmount
                            .multiply(coupon.getDiscountValue())
                            .divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
            case FIXED_AMOUNT ->
                    coupon.getDiscountValue().setScale(SCALE, ROUNDING);
        };
    }
}

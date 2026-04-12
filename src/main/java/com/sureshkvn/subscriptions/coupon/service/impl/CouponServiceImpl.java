package com.sureshkvn.subscriptions.coupon.service.impl;

import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import com.sureshkvn.subscriptions.coupon.dto.CouponRequest;
import com.sureshkvn.subscriptions.coupon.dto.CouponResponse;
import com.sureshkvn.subscriptions.coupon.dto.DiscountResult;
import com.sureshkvn.subscriptions.coupon.mapper.CouponMapper;
import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;
import com.sureshkvn.subscriptions.coupon.repository.CouponRepository;
import com.sureshkvn.subscriptions.coupon.service.CouponService;
import com.sureshkvn.subscriptions.coupon.service.DiscountCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Default implementation of {@link CouponService}.
 *
 * <h2>Stackability enforcement</h2>
 * <pre>
 *   Rule A: A non-stackable coupon cannot coexist with ANY other coupon on a subscription.
 *   Rule B: Any coupon cannot be added to a subscription that already holds a non-stackable coupon.
 *   Rule C: The same coupon code cannot be applied twice to the same subscription.
 * </pre>
 *
 * <h2>Discount floor</h2>
 * <p>The total discount computed by {@link DiscountCalculator} is capped at the base amount,
 * ensuring the final subscription price is always ≥ 0.00 in the plan's currency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;
    private final DiscountCalculator discountCalculator;

    // -------------------------------------------------------------------------
    // Coupon management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        log.info("Creating coupon: {}", request.code());

        if (couponRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(
                    "A coupon with code '" + request.code() + "' already exists");
        }

        validateDiscountValue(request.discountType(), request.discountValue());

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Expiry date must be in the future");
        }

        Coupon coupon = couponMapper.toEntity(request);
        coupon.setStatus(Coupon.CouponStatus.ACTIVE);
        return couponMapper.toResponse(couponRepository.save(coupon));
    }

    @Override
    public CouponResponse getCouponByCode(String code) {
        return couponMapper.toResponse(findByCodeOrThrow(code));
    }

    @Override
    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(couponMapper::toResponse)
                .toList();
    }

    @Override
    public List<CouponResponse> getCouponsByStatus(Coupon.CouponStatus status) {
        return couponRepository.findAllByStatus(status).stream()
                .map(couponMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CouponResponse deactivateCoupon(String code) {
        log.info("Deactivating coupon: {}", code);
        Coupon coupon = findByCodeOrThrow(code);

        if (coupon.getStatus() == Coupon.CouponStatus.INACTIVE) {
            throw new BusinessRuleException("Coupon '" + code + "' is already inactive");
        }

        coupon.setStatus(Coupon.CouponStatus.INACTIVE);
        return couponMapper.toResponse(couponRepository.save(coupon));
    }

    // -------------------------------------------------------------------------
    // Discount application
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public List<DiscountResult> validateAndRedeem(List<String> codes, BigDecimal baseAmount) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }

        // Resolve all coupons first — fail fast if any code is unknown
        List<Coupon> coupons = codes.stream()
                .map(this::findByCodeOrThrow)
                .toList();

        // Validate each coupon individually
        coupons.forEach(c -> validateCouponEligibility(c, baseAmount));

        // Validate the set as a whole for stackability
        validateStackabilityAmongNewCoupons(coupons);

        // Redeem: increment redemption counts and build results
        return coupons.stream()
                .map(c -> {
                    c.incrementRedemptions();
                    couponRepository.save(c);
                    BigDecimal discount = discountCalculator.computeSingleDiscount(baseAmount, c);
                    log.debug("Coupon '{}' contributes discount: {}", c.getCode(), discount);
                    return new DiscountResult(c, discount);
                })
                .toList();
    }

    @Override
    @Transactional
    public DiscountResult validateAndRedeemOne(String code, BigDecimal baseAmount,
                                               List<SubscriptionCoupon> existingActiveCoupons) {
        Coupon coupon = findByCodeOrThrow(code);

        // Individual eligibility checks
        validateCouponEligibility(coupon, baseAmount);

        // Duplicate check — same code cannot be applied twice
        boolean alreadyApplied = existingActiveCoupons.stream()
                .anyMatch(sc -> sc.getCoupon().getCode().equalsIgnoreCase(code));
        if (alreadyApplied) {
            throw new BusinessRuleException(
                    "Coupon '" + code + "' is already applied to this subscription");
        }

        // Stackability check against existing coupons
        validateStackabilityAgainstExisting(coupon, existingActiveCoupons);

        coupon.incrementRedemptions();
        couponRepository.save(coupon);

        BigDecimal discount = discountCalculator.computeSingleDiscount(baseAmount, coupon);
        log.debug("Coupon '{}' contributes discount: {}", coupon.getCode(), discount);
        return new DiscountResult(coupon, discount);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Coupon findByCodeOrThrow(String code) {
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "code", code));
    }

    /**
     * Validates that a coupon is currently eligible for application.
     * Checks: active status, expiry, redemption limit, minimum amount.
     */
    private void validateCouponEligibility(Coupon coupon, BigDecimal baseAmount) {
        if (coupon.getStatus() != Coupon.CouponStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Coupon '" + coupon.getCode() + "' is not active");
        }

        if (coupon.getExpiresAt() != null && Instant.now().isAfter(coupon.getExpiresAt())) {
            throw new BusinessRuleException(
                    "Coupon '" + coupon.getCode() + "' has expired");
        }

        if (coupon.getMaxRedemptions() != null
                && coupon.getCurrentRedemptions() >= coupon.getMaxRedemptions()) {
            throw new BusinessRuleException(
                    "Coupon '" + coupon.getCode() + "' has reached its redemption limit");
        }

        if (coupon.getMinimumAmount() != null
                && baseAmount.compareTo(coupon.getMinimumAmount()) < 0) {
            throw new BusinessRuleException(
                    "Coupon '" + coupon.getCode() + "' requires a minimum plan price of "
                            + coupon.getMinimumAmount() + " but plan price is " + baseAmount);
        }
    }

    /**
     * Validates stackability within a set of coupons being applied together (new subscription).
     *
     * <p>Rule: if ANY coupon in the set is non-stackable, the set must contain exactly one coupon.
     */
    private void validateStackabilityAmongNewCoupons(List<Coupon> coupons) {
        if (coupons.size() <= 1) return;

        boolean anyNonStackable = coupons.stream().anyMatch(c -> !c.isStackable());
        if (anyNonStackable) {
            List<String> nonStackableCodes = coupons.stream()
                    .filter(c -> !c.isStackable())
                    .map(Coupon::getCode)
                    .toList();
            throw new BusinessRuleException(
                    "Coupon(s) " + nonStackableCodes + " are non-stackable and cannot be "
                            + "combined with other coupons");
        }
    }

    /**
     * Validates stackability when adding ONE new coupon to a subscription that already
     * has active coupons.
     *
     * <p>Rule A: The new coupon is non-stackable → cannot be added if any existing coupon present.
     * <p>Rule B: An existing coupon is non-stackable → no new coupon can be added.
     */
    private void validateStackabilityAgainstExisting(Coupon newCoupon,
                                                      List<SubscriptionCoupon> existing) {
        if (existing.isEmpty()) return;

        // Rule A
        if (!newCoupon.isStackable()) {
            throw new BusinessRuleException(
                    "Coupon '" + newCoupon.getCode() + "' is non-stackable and cannot be added "
                            + "to a subscription that already has other coupons applied");
        }

        // Rule B
        boolean existingHasNonStackable = existing.stream()
                .anyMatch(sc -> !sc.getCoupon().isStackable());
        if (existingHasNonStackable) {
            List<String> nonStackableCodes = existing.stream()
                    .filter(sc -> !sc.getCoupon().isStackable())
                    .map(sc -> sc.getCoupon().getCode())
                    .toList();
            throw new BusinessRuleException(
                    "Cannot add coupon '" + newCoupon.getCode() + "' because the subscription "
                            + "already holds non-stackable coupon(s): " + nonStackableCodes);
        }
    }

    /**
     * Cross-field validation for discount value based on discount type.
     * PERCENT coupons must have a value between 0 (exclusive) and 100 (inclusive).
     */
    private void validateDiscountValue(Coupon.DiscountType type, BigDecimal value) {
        if (type == Coupon.DiscountType.PERCENT) {
            if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessRuleException(
                        "PERCENT discount value cannot exceed 100 (got " + value + ")");
            }
        }
    }
}

package com.sureshkvn.subscriptions.subscription.mapper;

import com.sureshkvn.subscriptions.coupon.mapper.CouponMapper;
import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;
import com.sureshkvn.subscriptions.coupon.service.DiscountCalculator;
import com.sureshkvn.subscriptions.plan.mapper.PlanMapper;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

/**
 * MapStruct mapper for {@link Subscription} entity → {@link SubscriptionResponse} DTO.
 *
 * <p>Uses {@link PlanMapper} for the nested plan and {@link CouponMapper} for the
 * {@code appliedCoupons} collection (via the {@code toAppliedCouponResponse} method).
 *
 * <p>The abstract class pattern (instead of interface) is required here so that
 * {@link DiscountCalculator} can be {@code @Autowired} and used in the
 * {@link #computeDiscountFields} post-mapping hook to fill in the computed fields
 * ({@code totalDiscountAmount}, {@code effectivePrice}) before the Lombok builder
 * calls {@code .build()}.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {PlanMapper.class, CouponMapper.class},
        builder = @Builder(disableBuilder = false)
)
public abstract class SubscriptionMapper {

    @Autowired
    protected DiscountCalculator discountCalculator;

    /**
     * Maps a {@link Subscription} to {@link SubscriptionResponse}.
     *
     * <p>The computed fields ({@code totalDiscountAmount}, {@code effectivePrice}) are set
     * by {@link #computeDiscountFields} after the main structural mapping.
     */
    @Mapping(source = "plan",           target = "plan")
    @Mapping(source = "appliedCoupons", target = "appliedCoupons")
    @Mapping(target = "totalDiscountAmount", ignore = true)
    @Mapping(target = "effectivePrice",      ignore = true)
    public abstract SubscriptionResponse toResponse(Subscription subscription);

    /**
     * Post-mapping hook: computes {@code totalDiscountAmount} and {@code effectivePrice}
     * from the subscription's active coupons and injects them into the builder before
     * {@code .build()} is called.
     *
     * <p>Only <em>active</em> (non-revoked) coupons contribute to the totals.
     * If there are no active coupons, {@code totalDiscountAmount = 0} and
     * {@code effectivePrice = plan.price}.
     */
    @AfterMapping
    protected void computeDiscountFields(Subscription subscription,
            @MappingTarget SubscriptionResponse.SubscriptionResponseBuilder builder) {

        List<SubscriptionCoupon> activeCoupons = subscription.getActiveCoupons();
        BigDecimal base = subscription.getPlan().getPrice();

        if (activeCoupons.isEmpty()) {
            builder.totalDiscountAmount(BigDecimal.ZERO);
            builder.effectivePrice(base);
            return;
        }

        var activeCouponEntities = activeCoupons.stream()
                .map(SubscriptionCoupon::getCoupon)
                .toList();

        BigDecimal totalDiscount  = discountCalculator.computeTotalDiscount(base, activeCouponEntities);
        BigDecimal effectivePrice = discountCalculator.computeFinalAmount(base, activeCouponEntities);

        builder.totalDiscountAmount(totalDiscount);
        builder.effectivePrice(effectivePrice);
    }
}

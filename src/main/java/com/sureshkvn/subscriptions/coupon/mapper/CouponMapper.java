package com.sureshkvn.subscriptions.coupon.mapper;

import com.sureshkvn.subscriptions.coupon.dto.AppliedCouponResponse;
import com.sureshkvn.subscriptions.coupon.dto.CouponRequest;
import com.sureshkvn.subscriptions.coupon.dto.CouponResponse;
import com.sureshkvn.subscriptions.coupon.model.Coupon;
import com.sureshkvn.subscriptions.coupon.model.SubscriptionCoupon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for coupon-related entity ↔ DTO conversions.
 *
 * <p>Also handles {@link SubscriptionCoupon} → {@link AppliedCouponResponse} mapping,
 * which is used by {@link com.sureshkvn.subscriptions.subscription.mapper.SubscriptionMapper}
 * when mapping the {@code appliedCoupons} collection.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CouponMapper {

    Coupon toEntity(CouponRequest request);

    CouponResponse toResponse(Coupon coupon);

    /**
     * Maps a {@link SubscriptionCoupon} (join entity) to {@link AppliedCouponResponse},
     * flattening the nested {@link Coupon} fields into the response.
     */
    @Mapping(source = "coupon.code",          target = "code")
    @Mapping(source = "coupon.description",   target = "description")
    @Mapping(source = "coupon.discountType",  target = "discountType")
    @Mapping(source = "coupon.discountValue", target = "discountValue")
    @Mapping(source = "coupon.stackable",     target = "stackable")
    @Mapping(source = "discountAmount",       target = "discountAmount")
    @Mapping(source = "active",               target = "active")
    @Mapping(source = "appliedAt",            target = "appliedAt")
    AppliedCouponResponse toAppliedCouponResponse(SubscriptionCoupon subscriptionCoupon);
}

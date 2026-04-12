package com.sureshkvn.subscriptions.billing.mapper;

import com.sureshkvn.subscriptions.billing.dto.BillingCycleResponse;
import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for {@link BillingCycle} entity ↔ DTO conversions.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BillingMapper {

    @Mapping(source = "subscription.id", target = "subscriptionId")
    BillingCycleResponse toResponse(BillingCycle billingCycle);
}

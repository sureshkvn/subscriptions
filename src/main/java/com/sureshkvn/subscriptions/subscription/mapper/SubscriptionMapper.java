package com.sureshkvn.subscriptions.subscription.mapper;

import com.sureshkvn.subscriptions.plan.mapper.PlanMapper;
import com.sureshkvn.subscriptions.subscription.dto.SubscriptionResponse;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for {@link Subscription} entity ↔ DTO conversions.
 *
 * <p>Uses {@link PlanMapper} as a dependency to map the nested plan association.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = PlanMapper.class
)
public interface SubscriptionMapper {

    @Mapping(source = "plan", target = "plan")
    SubscriptionResponse toResponse(Subscription subscription);
}

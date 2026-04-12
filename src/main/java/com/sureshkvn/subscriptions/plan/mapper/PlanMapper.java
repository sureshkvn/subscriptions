package com.sureshkvn.subscriptions.plan.mapper;

import com.sureshkvn.subscriptions.plan.dto.PlanRequest;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.model.Plan;
import org.mapstruct.*;

/**
 * MapStruct mapper for {@link Plan} entity ↔ DTO conversions.
 *
 * <p>Spring component model is used so the mapper is injectable as a Spring bean.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PlanMapper {

    Plan toEntity(PlanRequest request);

    PlanResponse toResponse(Plan plan);

    /**
     * Applies non-null fields from {@code request} onto the existing {@code plan} entity.
     * Used for PATCH-style partial updates.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(PlanRequest request, @MappingTarget Plan plan);
}

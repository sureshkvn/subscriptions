package com.sureshkvn.subscriptions.plan.service;

import com.sureshkvn.subscriptions.plan.dto.PlanRequest;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.model.Plan;

import java.util.List;

/**
 * Service interface for subscription plan lifecycle operations.
 */
public interface PlanService {

    PlanResponse createPlan(PlanRequest request);

    PlanResponse getPlanById(Long id);

    List<PlanResponse> getAllPlans();

    List<PlanResponse> getPlansByStatus(Plan.PlanStatus status);

    PlanResponse updatePlan(Long id, PlanRequest request);

    PlanResponse archivePlan(Long id);

    void deletePlan(Long id);
}

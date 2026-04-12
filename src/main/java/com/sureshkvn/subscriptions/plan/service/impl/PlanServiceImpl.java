package com.sureshkvn.subscriptions.plan.service.impl;

import com.sureshkvn.subscriptions.common.exception.BusinessRuleException;
import com.sureshkvn.subscriptions.common.exception.ResourceNotFoundException;
import com.sureshkvn.subscriptions.plan.dto.PlanRequest;
import com.sureshkvn.subscriptions.plan.dto.PlanResponse;
import com.sureshkvn.subscriptions.plan.mapper.PlanMapper;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.plan.repository.PlanRepository;
import com.sureshkvn.subscriptions.plan.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default implementation of {@link PlanService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    @Override
    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        log.info("Creating plan with name: {}", request.name());

        if (planRepository.existsByName(request.name())) {
            throw new BusinessRuleException(
                    "A plan with name '" + request.name() + "' already exists");
        }

        Plan plan = planMapper.toEntity(request);
        plan.setStatus(Plan.PlanStatus.ACTIVE);
        Plan saved = planRepository.save(plan);

        log.info("Plan created with id: {}", saved.getId());
        return planMapper.toResponse(saved);
    }

    @Override
    public PlanResponse getPlanById(Long id) {
        return planMapper.toResponse(findPlanOrThrow(id));
    }

    @Override
    public List<PlanResponse> getAllPlans() {
        return planRepository.findAll().stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    public List<PlanResponse> getPlansByStatus(Plan.PlanStatus status) {
        return planRepository.findAllByStatus(status).stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(Long id, PlanRequest request) {
        log.info("Updating plan id: {}", id);
        Plan plan = findPlanOrThrow(id);

        if (plan.getStatus() == Plan.PlanStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot update an archived plan");
        }

        // Check name uniqueness only if name is changing
        if (!plan.getName().equals(request.name())
                && planRepository.existsByName(request.name())) {
            throw new BusinessRuleException(
                    "A plan with name '" + request.name() + "' already exists");
        }

        planMapper.updateEntityFromRequest(request, plan);
        return planMapper.toResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public PlanResponse archivePlan(Long id) {
        log.info("Archiving plan id: {}", id);
        Plan plan = findPlanOrThrow(id);

        if (plan.getStatus() == Plan.PlanStatus.ARCHIVED) {
            throw new BusinessRuleException("Plan is already archived");
        }

        plan.setStatus(Plan.PlanStatus.ARCHIVED);
        return planMapper.toResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        log.info("Deleting plan id: {}", id);
        Plan plan = findPlanOrThrow(id);

        if (plan.getStatus() == Plan.PlanStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot delete an active plan. Archive it first.");
        }

        planRepository.delete(plan);
    }

    private Plan findPlanOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", id));
    }
}

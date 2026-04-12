package com.sureshkvn.subscriptions.plan.repository;

import com.sureshkvn.subscriptions.plan.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Plan} entities.
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByName(String name);

    boolean existsByName(String name);

    List<Plan> findAllByStatus(Plan.PlanStatus status);

    List<Plan> findAllByBillingInterval(Plan.BillingInterval billingInterval);
}

package com.sureshkvn.subscriptions.billing.repository;

import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link BillingCycle} entities.
 */
@Repository
public interface BillingCycleRepository extends JpaRepository<BillingCycle, Long> {

    List<BillingCycle> findAllBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    List<BillingCycle> findAllByStatus(BillingCycle.BillingStatus status);
}

package com.sureshkvn.subscriptions.billing.repository;

import com.sureshkvn.subscriptions.billing.model.BillingCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link BillingCycle} entities.
 */
@Repository
public interface BillingCycleRepository extends JpaRepository<BillingCycle, Long> {

    List<BillingCycle> findAllBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    List<BillingCycle> findAllByStatus(BillingCycle.BillingStatus status);

    /**
     * Idempotency check: returns an existing billing cycle for the given subscription and period
     * start, if one was already created. Used to detect and skip duplicate Pub/Sub deliveries
     * without charging the customer twice.
     */
    Optional<BillingCycle> findBySubscriptionIdAndPeriodStart(Long subscriptionId, Instant periodStart);
}

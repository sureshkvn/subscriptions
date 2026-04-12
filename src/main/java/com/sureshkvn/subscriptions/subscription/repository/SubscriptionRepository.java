package com.sureshkvn.subscriptions.subscription.repository;

import com.sureshkvn.subscriptions.subscription.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Subscription} entities.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByCustomerId(String customerId);

    List<Subscription> findAllByStatus(Subscription.SubscriptionStatus status);

    List<Subscription> findAllByCustomerIdAndStatus(
            String customerId, Subscription.SubscriptionStatus status);

    boolean existsByCustomerIdAndPlanIdAndStatusIn(
            String customerId, Long planId, List<Subscription.SubscriptionStatus> statuses);

    /**
     * Finds subscriptions whose current period has ended (due for renewal or expiry).
     */
    @Query("SELECT s FROM Subscription s WHERE s.currentPeriodEnd < :now AND s.status = 'ACTIVE'")
    List<Subscription> findExpiredActiveSubscriptions(@Param("now") Instant now);
}

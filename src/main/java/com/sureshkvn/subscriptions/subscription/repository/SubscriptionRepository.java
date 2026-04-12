package com.sureshkvn.subscriptions.subscription.repository;

import com.sureshkvn.subscriptions.plan.model.Plan;
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

    /**
     * Partitioned query for the billing dispatcher job.
     *
     * <p>Returns ACTIVE subscriptions whose {@code currentPeriodEnd} is at or before {@code now},
     * filtered to the specified billing intervals, and restricted to this task's shard via
     * {@code MOD(s.id, :taskCount) = :taskIndex}. This ensures parallel Cloud Run task instances
     * process non-overlapping sets of subscriptions without distributed locking.
     *
     * @param intervals  list of billing intervals to include in this run (e.g. HOURLY or MONTHLY)
     * @param status     subscription status filter — typically {@code ACTIVE}
     * @param now        watermark time; only subscriptions with {@code currentPeriodEnd <= now} match
     * @param taskCount  total number of Cloud Run task instances (from {@code CLOUD_RUN_TASK_COUNT})
     * @param taskIndex  zero-based index of this task instance (from {@code CLOUD_RUN_TASK_INDEX})
     */
    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan p
            WHERE p.billingInterval IN :intervals
              AND s.status = :status
              AND s.currentPeriodEnd <= :now
              AND MOD(s.id, :taskCount) = :taskIndex
            """)
    List<Subscription> findDueForBillingPartitioned(
            @Param("intervals")  List<Plan.BillingInterval> intervals,
            @Param("status")     Subscription.SubscriptionStatus status,
            @Param("now")        Instant now,
            @Param("taskCount")  int taskCount,
            @Param("taskIndex")  int taskIndex);
}

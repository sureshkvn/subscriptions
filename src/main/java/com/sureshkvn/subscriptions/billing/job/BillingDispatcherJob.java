package com.sureshkvn.subscriptions.billing.job;

import com.sureshkvn.subscriptions.billing.messaging.BillingDueEvent;
import com.sureshkvn.subscriptions.billing.messaging.BillingEventPublisher;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cloud Run Job entry-point that dispatches billing events to Pub/Sub.
 *
 * <h2>Execution model</h2>
 * <ol>
 *   <li>Cloud Scheduler triggers this job on a schedule (e.g. every hour).</li>
 *   <li>Cloud Run creates {@code CLOUD_RUN_TASK_COUNT} parallel task instances.</li>
 *   <li>Each task uses {@code MOD(subscription.id, taskCount) = taskIndex} partitioning
 *       so the work is distributed without overlap.</li>
 *   <li>The task queries subscriptions that are due for the configured intervals,
 *       publishes a {@link BillingDueEvent} per subscription, and exits.</li>
 * </ol>
 *
 * <h2>Environment variables</h2>
 * <ul>
 *   <li>{@code BILLING_INTERVALS} — comma-separated list of
 *       {@link Plan.BillingInterval} values to process in this run
 *       (e.g. {@code HOURLY} or {@code DAILY,WEEKLY,MONTHLY,YEARLY}).
 *       Defaults to all intervals.</li>
 *   <li>{@code CLOUD_RUN_TASK_INDEX} — zero-based index of this task instance (auto-injected).</li>
 *   <li>{@code CLOUD_RUN_TASK_COUNT} — total number of task instances (auto-injected).</li>
 * </ul>
 *
 * <p>Active only under the {@code job} Spring profile.
 */
@Slf4j
@Component
@Profile("job")
@RequiredArgsConstructor
public class BillingDispatcherJob implements ApplicationRunner {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingEventPublisher   billingEventPublisher;

    @Override
    public void run(ApplicationArguments args) {
        int taskIndex = envInt("CLOUD_RUN_TASK_INDEX", 0);
        int taskCount = envInt("CLOUD_RUN_TASK_COUNT", 1);
        List<Plan.BillingInterval> intervals = parseIntervals();

        log.info("BillingDispatcherJob starting: taskIndex={} taskCount={} intervals={}",
                taskIndex, taskCount, intervals);

        Instant now = Instant.now();
        List<Subscription> due = subscriptionRepository
                .findDueForBillingPartitioned(intervals, Subscription.SubscriptionStatus.ACTIVE,
                        now, taskCount, taskIndex);

        log.info("Found {} subscriptions due for billing (task {}/{})",
                due.size(), taskIndex, taskCount);

        int published = 0;
        int failed    = 0;

        for (Subscription sub : due) {
            try {
                BillingDueEvent event = BillingDueEvent.of(
                        sub.getId(),
                        sub.getCurrentPeriodEnd(),
                        nextPeriodEnd(sub),
                        sub.getPlan().getBillingInterval().name()
                );
                billingEventPublisher.publish(event);
                published++;
            } catch (Exception e) {
                log.error("Failed to publish billing event for subscription={}",
                        sub.getId(), e);
                failed++;
            }
        }

        log.info("BillingDispatcherJob finished: published={} failed={}", published, failed);

        if (failed > 0) {
            // Non-zero exit code causes Cloud Run to mark the task as FAILED.
            // Cloud Scheduler can then alert / retry the entire job.
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the end of the next billing period based on the subscription's plan interval.
     * The dispatcher sets this so the processor records accurate period boundaries.
     */
    private Instant nextPeriodEnd(Subscription sub) {
        Plan plan = sub.getPlan();
        Instant currentEnd = sub.getCurrentPeriodEnd();
        int count = plan.getIntervalCount() > 0 ? plan.getIntervalCount() : 1;

        return switch (plan.getBillingInterval()) {
            case HOURLY  -> currentEnd.plusSeconds(3_600L * count);
            case DAILY   -> currentEnd.plusSeconds(86_400L * count);
            case WEEKLY  -> currentEnd.plusSeconds(604_800L * count);
            case MONTHLY -> currentEnd.plusSeconds(2_592_000L * count); // ~30 days
            case YEARLY  -> currentEnd.plusSeconds(31_536_000L * count);
            case CUSTOM  -> currentEnd.plusSeconds(3_600L * count); // fallback to hours
        };
    }

    private List<Plan.BillingInterval> parseIntervals() {
        String raw = System.getenv("BILLING_INTERVALS");
        if (raw == null || raw.isBlank()) {
            return Arrays.asList(Plan.BillingInterval.values());
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(Plan.BillingInterval::valueOf)
                .collect(Collectors.toList());
    }

    private int envInt(String name, int defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("Could not parse env var {}='{}', using default {}", name, val, defaultValue);
            return defaultValue;
        }
    }
}

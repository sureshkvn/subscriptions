package com.sureshkvn.subscriptions.billing;

import com.sureshkvn.subscriptions.billing.job.BillingDispatcherJob;
import com.sureshkvn.subscriptions.billing.messaging.BillingDueEvent;
import com.sureshkvn.subscriptions.billing.messaging.BillingEventPublisher;
import com.sureshkvn.subscriptions.plan.model.Plan;
import com.sureshkvn.subscriptions.subscription.model.Subscription;
import com.sureshkvn.subscriptions.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link BillingDispatcherJob}.
 *
 * <p>No Spring context — mocks repository and publisher to verify the job's
 * dispatch logic without any I/O.
 */
@ExtendWith(MockitoExtension.class)
class BillingDispatcherJobTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock BillingEventPublisher  billingEventPublisher;

    @InjectMocks BillingDispatcherJob job;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Subscription buildActiveSubscription(Long id) {
        Plan plan = Plan.builder()
                .id(1L)
                .name("Monthly Pro")
                .price(new BigDecimal("99.00"))
                .currency("USD")
                .billingInterval(Plan.BillingInterval.MONTHLY)
                .intervalCount(1)
                .status(Plan.PlanStatus.ACTIVE)
                .build();

        return Subscription.builder()
                .id(id)
                .customerId("customer-" + id)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minusSeconds(2_592_000))
                .currentPeriodEnd(Instant.now().minusSeconds(60))  // due 1 minute ago
                .build();
    }

    @BeforeEach
    void clearEnv() {
        // Tests run in the current JVM — CLOUD_RUN_TASK_INDEX/COUNT are not set,
        // so the job defaults to taskIndex=0, taskCount=1 (single-task mode).
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Publishes one BillingDueEvent per due subscription")
    void publishes_event_per_due_subscription() throws Exception {
        List<Subscription> due = List.of(
                buildActiveSubscription(1L),
                buildActiveSubscription(2L),
                buildActiveSubscription(3L)
        );

        when(subscriptionRepository.findDueForBillingPartitioned(anyList(), any(), any(), anyInt(), anyInt()))
                .thenReturn(due);

        job.run(new DefaultApplicationArguments());

        ArgumentCaptor<BillingDueEvent> captor = ArgumentCaptor.forClass(BillingDueEvent.class);
        verify(billingEventPublisher, times(3)).publish(captor.capture());

        List<BillingDueEvent> events = captor.getAllValues();
        assertThat(events).extracting(BillingDueEvent::subscriptionId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(events).allSatisfy(e ->
                assertThat(e.billingInterval()).isEqualTo("MONTHLY"));
    }

    @Test
    @DisplayName("Continues publishing remaining events when one publish fails")
    void continues_on_publish_failure() throws Exception {
        List<Subscription> due = List.of(
                buildActiveSubscription(10L),
                buildActiveSubscription(20L)
        );

        when(subscriptionRepository.findDueForBillingPartitioned(anyList(), any(), any(), anyInt(), anyInt()))
                .thenReturn(due);

        // First call succeeds, second throws
        doNothing()
                .doThrow(new RuntimeException("Pub/Sub unavailable"))
                .when(billingEventPublisher).publish(any());

        // Job should NOT propagate the exception — it logs and counts failures.
        // The job exits with System.exit(1) but in tests we can't assert that directly;
        // we verify that publish was attempted for both subscriptions.
        try {
            job.run(new DefaultApplicationArguments());
        } catch (Exception ignored) {
            // System.exit() may be intercepted in some test runners — ignore.
        }

        verify(billingEventPublisher, times(2)).publish(any());
    }

    @Test
    @DisplayName("Does not publish when no subscriptions are due")
    void no_subscriptions_due_publishes_nothing() throws Exception {
        when(subscriptionRepository.findDueForBillingPartitioned(anyList(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        job.run(new DefaultApplicationArguments());

        verify(billingEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Passes correct periodStart (currentPeriodEnd) to the event")
    void event_period_start_equals_current_period_end() throws Exception {
        Subscription sub = buildActiveSubscription(42L);
        Instant expectedPeriodStart = sub.getCurrentPeriodEnd();

        when(subscriptionRepository.findDueForBillingPartitioned(anyList(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sub));

        job.run(new DefaultApplicationArguments());

        ArgumentCaptor<BillingDueEvent> captor = ArgumentCaptor.forClass(BillingDueEvent.class);
        verify(billingEventPublisher).publish(captor.capture());

        assertThat(captor.getValue().periodStart()).isEqualTo(expectedPeriodStart);
    }
}

package com.sureshkvn.subscriptions.billing.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Publishes {@link BillingDueEvent} messages to the Pub/Sub topic
 * configured by {@code billing.pubsub.topic}.
 *
 * <p>Used exclusively by {@link com.sureshkvn.subscriptions.billing.job.BillingDispatcherJob}
 * during the dispatch phase. The API service never publishes — it only consumes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventPublisher {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${billing.pubsub.topic:subscription.billing.due}")
    private String topicName;

    /**
     * Serialises {@code event} to JSON and publishes it to the billing topic.
     *
     * @param event the billing-due event to publish
     * @throws IllegalStateException if serialisation or publish fails
     */
    public void publish(BillingDueEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise BillingDueEvent for subscription "
                    + event.subscriptionId(), e);
        }

        try {
            String messageId = pubSubTemplate.publish(topicName, payload).get();
            log.debug("Published billing event for subscription={} messageId={}",
                    event.subscriptionId(), messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing billing event", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish billing event for subscription "
                    + event.subscriptionId(), e.getCause());
        }
    }
}

package com.sureshkvn.subscriptions.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import com.google.cloud.spring.pubsub.support.converter.JacksonPubSubMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google Cloud Pub/Sub configuration.
 *
 * <p>This service uses <strong>HTTP push</strong> delivery (Pub/Sub calls
 * {@code POST /internal/billing/process} directly), so no inbound channel adapter
 * is configured here. The push subscription is provisioned via {@code infra/pubsub-setup.sh}.
 *
 * <p>The {@link JacksonPubSubMessageConverter} bean is registered so that any
 * future pull-based subscriptions can benefit from automatic JSON deserialisation.
 */
@Configuration
public class PubSubConfig {

    /**
     * Configures Jackson as the message converter for Pub/Sub, enabling automatic
     * serialisation/deserialisation of Java objects to/from JSON payloads.
     */
    @Bean
    public JacksonPubSubMessageConverter jacksonPubSubMessageConverter(ObjectMapper objectMapper) {
        return new JacksonPubSubMessageConverter(objectMapper);
    }
}

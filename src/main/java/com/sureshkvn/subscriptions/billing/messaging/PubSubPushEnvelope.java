package com.sureshkvn.subscriptions.billing.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Wrapper that Pub/Sub uses when delivering messages via HTTP push to the API service.
 *
 * <p>Pub/Sub wraps the actual message in a {@code message} field containing a
 * Base64-encoded {@code data} payload plus metadata attributes. This record
 * models that envelope so Spring MVC can deserialise it automatically.
 *
 * <pre>{@code
 * {
 *   "message": {
 *     "data": "<base64-encoded BillingDueEvent JSON>",
 *     "messageId": "...",
 *     "publishTime": "...",
 *     "attributes": {}
 *   },
 *   "subscription": "projects/my-project/subscriptions/billing-due-sub"
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PubSubPushEnvelope(
        Message message,
        String subscription
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            /** Base64-encoded JSON payload. */
            String data,
            String messageId,
            String publishTime,
            Map<String, String> attributes
    ) {}
}

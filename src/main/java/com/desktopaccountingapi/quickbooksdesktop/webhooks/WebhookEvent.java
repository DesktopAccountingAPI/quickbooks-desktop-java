package com.desktopaccountingapi.quickbooksdesktop.webhooks;

import com.desktopaccountingapi.quickbooksdesktop.core.Json;
import com.desktopaccountingapi.quickbooksdesktop.core.JsonWritable;
import com.desktopaccountingapi.quickbooksdesktop.core.Wire;
import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A verified webhook event. Events are thin: fetch the full object through the API (for request
 * events, {@code client.requests().retrieve(dataId())}).
 */
public final class WebhookEvent implements JsonWritable {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList("id", "type", "timestamp", "projectId", "data"));

    private final String id;
    private final String type;
    private final OffsetDateTime timestamp;
    private final String projectId;
    private final Map<String, Object> data;
    private final Map<String, Object> additionalProperties;

    private WebhookEvent(String id, String type, OffsetDateTime timestamp, String projectId, Map<String, Object> data, Map<String, Object> extra) {
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
        this.projectId = projectId;
        this.data = data;
        this.additionalProperties = extra;
    }

    static WebhookEvent parse(String payload) {
        try {
            Map<String, Object> o = Wire.object(Json.parse(payload), "webhook event");
            String id = Wire.get(o, "id", Wire::asString);
            String type = Wire.get(o, "type", Wire::asString);
            if (id == null || type == null) throw new WebhookVerificationException("webhook body has no id or type");
            Map<String, Object> data = Wire.get(o, "data", Wire::asMap);
            return new WebhookEvent(id, type, Wire.get(o, "timestamp", Wire::asDateTime), Wire.get(o, "projectId", Wire::asString),
                data == null ? Collections.emptyMap() : data, Wire.extra(o, KNOWN));
        } catch (Json.JsonException e) {
            throw new WebhookVerificationException("webhook body is not a valid event: " + e.getMessage());
        }
    }

    /**
     * Event ID ({@code evt_...}); equals the {@code webhook-id} header. Use it to deduplicate.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Event type, for example {@code request.succeeded}. An open set: compare with
     * {@link WebhookEventType} and ignore types you do not handle.
     *
     * @return the type
     */
    public String type() {
        return type;
    }

    /**
     * When the event happened.
     *
     * @return the timestamp, or null if absent
     */
    public OffsetDateTime timestamp() {
        return timestamp;
    }

    /**
     * Project the event belongs to ({@code proj_...}).
     *
     * @return the project ID, or null
     */
    public String projectId() {
        return projectId;
    }

    /**
     * Event payload: for request events the request resource without {@code result} and
     * {@code timeline}; for connection events {@code connectionId}, {@code endUserId}, {@code status},
     * {@code previousStatus}, {@code reason} and {@code error}.
     *
     * @return unmodifiable map
     */
    public Map<String, Object> data() {
        return data;
    }

    /**
     * Members not known to this SDK version.
     *
     * @return unmodifiable map
     */
    public Map<String, Object> additionalProperties() {
        return additionalProperties;
    }

    @Override
    public Map<String, Object> toWire() {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("id", id);
        w.put("type", type);
        w.put("timestamp", timestamp);
        w.put("projectId", projectId);
        w.put("data", data);
        w.putAll(additionalProperties);
        return w;
    }

    @Override
    public String toString() {
        return "WebhookEvent" + toJson();
    }
}

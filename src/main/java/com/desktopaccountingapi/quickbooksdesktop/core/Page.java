package com.desktopaccountingapi.quickbooksdesktop.core;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One page of a cursor list.
 *
 * @param <T> item type
 */
public final class Page<T> implements JsonWritable {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList("objectType", "url", "data", "nextCursor", "hasMore", "remainingCount", "cursorExpiresAt"));

    private final String objectType;
    private final String url;
    private final List<T> data;
    private final List<Object> rawData;
    private final String nextCursor;
    private final boolean hasMore;
    private final Integer remainingCount;
    private final OffsetDateTime cursorExpiresAt;
    private final Map<String, Object> additionalProperties;
    private final String requestId;

    private Page(Map<String, Object> o, List<T> data, List<Object> rawData, String requestId) {
        this.objectType = Wire.get(o, "objectType", Wire::asString);
        this.url = Wire.get(o, "url", Wire::asString);
        this.data = data;
        this.rawData = rawData;
        this.nextCursor = Wire.get(o, "nextCursor", Wire::asString);
        this.hasMore = Boolean.TRUE.equals(Wire.get(o, "hasMore", Wire::asBoolean));
        this.remainingCount = Wire.get(o, "remainingCount", Wire::asInteger);
        this.cursorExpiresAt = Wire.get(o, "cursorExpiresAt", Wire::asDateTime);
        this.additionalProperties = Wire.extra(o, KNOWN);
        this.requestId = requestId;
    }

    /**
     * Parser for one page of a list, used for async-mode list results.
     *
     * @param item parser for one item
     * @param <T> item type
     * @return the page parser
     */
    public static <T> Function<Object, Page<T>> parser(Function<Object, T> item) {
        return json -> fromJson(json, item, null);
    }

    static <T> Page<T> fromJson(Object json, Function<Object, T> item, String requestId) {
        Map<String, Object> o = Wire.object(json, "list page");
        Object rawData = o.get("data");
        List<Object> raw = new ArrayList<>();
        if (rawData != null) {
            if (!(rawData instanceof List)) throw new Json.JsonException("member \"data\": expected an array");
            raw.addAll((List<?>) rawData);
        }
        List<T> items = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            try {
                items.add(raw.get(i) == null ? null : item.apply(raw.get(i)));
            } catch (RuntimeException e) {
                throw new Json.JsonException("data[" + i + "]: " + e.getMessage());
            }
        }
        return new Page<>(o, Collections.unmodifiableList(items), Collections.unmodifiableList(raw), requestId);
    }

    /**
     * Items on this page.
     *
     * @return unmodifiable list
     */
    public List<T> data() {
        return data;
    }

    List<Object> rawData() {
        return rawData;
    }

    /**
     * Cursor for the next page.
     *
     * @return the cursor, or null on the last page
     */
    public String nextCursor() {
        return nextCursor;
    }

    /**
     * Whether more pages follow.
     *
     * @return true if {@link #nextCursor()} leads to more records
     */
    public boolean hasMore() {
        return hasMore;
    }

    /**
     * Records left after this page, as reported by QuickBooks.
     *
     * @return the count, or null on the last page
     */
    public Integer remainingCount() {
        return remainingCount;
    }

    /**
     * The server's estimate of when the cursor expires if no continue request arrives.
     *
     * @return the deadline, or null on the last page
     */
    public OffsetDateTime cursorExpiresAt() {
        return cursorExpiresAt;
    }

    /**
     * Always {@code list}.
     *
     * @return the object type
     */
    public String objectType() {
        return objectType;
    }

    /**
     * Path of the list endpoint.
     *
     * @return the path
     */
    public String url() {
        return url;
    }

    /**
     * Members not known to this SDK version.
     *
     * @return unmodifiable map
     */
    public Map<String, Object> additionalProperties() {
        return additionalProperties;
    }

    /**
     * {@code Daapi-Request-Id} of the response that delivered this page.
     *
     * @return the request ID, or null
     */
    public String requestId() {
        return requestId;
    }

    @Override
    public Map<String, Object> toWire() {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("objectType", objectType);
        w.put("url", url);
        w.put("data", data);
        w.put("nextCursor", nextCursor);
        w.put("hasMore", hasMore);
        w.put("remainingCount", remainingCount);
        w.put("cursorExpiresAt", cursorExpiresAt);
        w.putAll(additionalProperties);
        return w;
    }

    @Override
    public String toString() {
        return "Page" + toJson();
    }
}

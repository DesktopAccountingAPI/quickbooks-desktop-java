package com.desktopaccountingapi.quickbooksdesktop.core;

import com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class of generated request bodies and query parameter objects.
 *
 * <p>Values live in an ordered map keyed by wire name, so a field that was never set is not sent,
 * while a field explicitly set to null on a clearable field is sent as JSON {@code null}. Setting a
 * non-clearable field to null removes it again.
 */
public abstract class InputObject implements JsonWritable {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final String[] required;

    /**
     * Creates an empty input.
     *
     * @param required wire names that must be set before the input is sent
     */
    protected InputObject(String... required) {
        this.required = required.clone();
    }

    /**
     * Stores a value that may be null; null is sent as JSON null (clears the field).
     *
     * @param name wire name
     * @param value value or null
     */
    protected final void setNullable(String name, Object value) {
        values.put(name, copy(value));
    }

    /**
     * Stores a value; null removes the field so it is not sent.
     *
     * @param name wire name
     * @param value value or null
     */
    protected final void setOptional(String name, Object value) {
        if (value == null) values.remove(name);
        else values.put(name, copy(value));
    }

    private static Object copy(Object value) {
        if (value instanceof Collection) return Collections.unmodifiableList(new ArrayList<>((Collection<?>) value));
        if (value instanceof Map) return Collections.unmodifiableMap(new LinkedHashMap<>((Map<?, ?>) value));
        return value;
    }

    /**
     * Reads a stored value.
     *
     * @param name wire name
     * @param <T> expected type
     * @return the value, or null when unset or set to null
     */
    @SuppressWarnings("unchecked")
    protected final <T> T value(String name) {
        return (T) values.get(name);
    }

    /**
     * Whether a field was set (including an explicit null).
     *
     * @param wireName wire (JSON) name of the field
     * @return true if the field will be sent
     */
    public final boolean isSet(String wireName) {
        return values.containsKey(wireName);
    }

    /**
     * The fields that will be sent, in the order they were first set.
     *
     * @return unmodifiable map of wire name to value
     */
    @Override
    public final Map<String, Object> toWire() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Checks that every required field (also in nested inputs) is set. Called by the client before
     * sending.
     *
     * @throws DaapiException naming the first missing field
     */
    public final void validate() {
        validate(getClass().getSimpleName());
    }

    private void validate(String path) {
        for (String r : required) {
            if (!values.containsKey(r)) throw new DaapiException(path + "." + r + " is required");
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof InputObject) ((InputObject) v).validate(path + "." + e.getKey());
            else if (v instanceof List) {
                int i = 0;
                for (Object item : (List<?>) v) {
                    if (item instanceof InputObject) ((InputObject) item).validate(path + "." + e.getKey() + "[" + i + "]");
                    i++;
                }
            }
        }
    }

    /**
     * Query string pairs. Arrays use repeated keys (form style, exploded); unset and null values are
     * skipped.
     *
     * @return name/value pairs in order
     */
    public final List<Map.Entry<String, String>> queryPairs() {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            if (v instanceof Iterable) {
                for (Object item : (Iterable<?>) v) {
                    if (item != null) out.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), queryValue(item)));
                }
            } else {
                out.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), queryValue(v)));
            }
        }
        return out;
    }

    static String queryValue(Object v) {
        if (v instanceof String) return (String) v;
        if (v instanceof BigDecimal) return ((BigDecimal) v).toPlainString();
        if (v instanceof LocalDate) return v.toString();
        if (v instanceof OffsetDateTime) return Json.formatDateTime((OffsetDateTime) v);
        if (v instanceof Number) return Json.write(v);
        return String.valueOf(v);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + toJson();
    }
}

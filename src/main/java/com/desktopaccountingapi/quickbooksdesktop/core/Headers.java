package com.desktopaccountingapi.quickbooksdesktop.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Immutable HTTP response headers with case-insensitive names. */
public final class Headers {
    private static final Headers EMPTY = new Headers(new TreeMap<>());
    private final Map<String, List<String>> values;

    private Headers(Map<String, List<String>> values) {
        this.values = values;
    }

    /**
     * Copies a header map; names are normalised to lower case.
     *
     * @param headers header names to values
     * @return the headers
     */
    public static Headers of(Map<String, ? extends List<String>> headers) {
        Map<String, List<String>> out = new TreeMap<>();
        for (Map.Entry<String, ? extends List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            out.computeIfAbsent(e.getKey().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).addAll(e.getValue());
        }
        for (Map.Entry<String, List<String>> e : out.entrySet()) e.setValue(Collections.unmodifiableList(e.getValue()));
        return new Headers(out);
    }

    /**
     * Builds headers from single values.
     *
     * @param headers header names to values
     * @return the headers
     */
    public static Headers ofSingle(Map<String, String> headers) {
        Map<String, List<String>> out = new TreeMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            out.computeIfAbsent(e.getKey().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(e.getValue());
        }
        return of(out);
    }

    /**
     * No headers.
     *
     * @return the empty instance
     */
    public static Headers empty() {
        return EMPTY;
    }

    /**
     * The first value of a header.
     *
     * @param name header name, any case
     * @return the value, or null if the header is absent
     */
    public String get(String name) {
        List<String> v = values.get(name.toLowerCase(Locale.ROOT));
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /**
     * Every value of a header.
     *
     * @param name header name, any case
     * @return the values (empty if absent)
     */
    public List<String> all(String name) {
        List<String> v = values.get(name.toLowerCase(Locale.ROOT));
        return v == null ? Collections.emptyList() : v;
    }

    /**
     * All headers.
     *
     * @return lower-case names to values, sorted by name
     */
    public Map<String, List<String>> toMap() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}

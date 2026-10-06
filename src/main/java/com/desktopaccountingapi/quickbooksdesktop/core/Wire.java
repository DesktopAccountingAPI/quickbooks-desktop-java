package com.desktopaccountingapi.quickbooksdesktop.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Readers used by the generated {@code fromJson} methods. Internal to the SDK; not a stable API.
 *
 * <p>Response parsing is tolerant: unknown members are kept in {@code additionalProperties()},
 * missing members read as null, and unknown enum values pass through as strings. A member whose
 * JSON type contradicts the contract (a string where a number is documented) raises
 * {@link Json.JsonException} naming the member.
 */
public final class Wire {
    private Wire() {}

    /**
     * Casts a parsed JSON value to an object.
     *
     * @param json parsed JSON
     * @param type model name for the error message
     * @return the members
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object json, String type) {
        if (!(json instanceof Map)) throw new Json.JsonException("expected a JSON object for " + type + ", got " + kind(json));
        return (Map<String, Object>) json;
    }

    /**
     * Reads one member with a converter, adding the member name to conversion errors.
     *
     * @param o the object
     * @param key member name
     * @param conv converter for a non-null value
     * @param <T> result type
     * @return the converted value, or null when the member is missing or null
     */
    public static <T> T get(Map<String, Object> o, String key, Function<Object, T> conv) {
        Object v = o.get(key);
        if (v == null) return null;
        try {
            return conv.apply(v);
        } catch (RuntimeException e) {
            throw new Json.JsonException("member \"" + key + "\": " + e.getMessage());
        }
    }

    /**
     * Reads an array member.
     *
     * @param o the object
     * @param key member name
     * @param item converter for each non-null element
     * @param <T> element type
     * @return an unmodifiable list, or null when the member is missing or null
     */
    public static <T> List<T> list(Map<String, Object> o, String key, Function<Object, T> item) {
        return get(o, key, v -> asList(v, item));
    }

    /**
     * Unknown members, in wire order.
     *
     * @param o the object
     * @param known member names the model declares
     * @return unmodifiable map of the remaining members
     */
    public static Map<String, Object> extra(Map<String, Object> o, Set<String> known) {
        Map<String, Object> out = null;
        for (Map.Entry<String, Object> e : o.entrySet()) {
            if (known.contains(e.getKey())) continue;
            if (out == null) out = new LinkedHashMap<>();
            out.put(e.getKey(), e.getValue());
        }
        return out == null ? Collections.emptyMap() : Collections.unmodifiableMap(out);
    }

    /**
     * Converts an array value.
     *
     * @param v parsed JSON array
     * @param item converter for each non-null element
     * @param <T> element type
     * @return unmodifiable list
     */
    public static <T> List<T> asList(Object v, Function<Object, T> item) {
        if (!(v instanceof List)) throw new Json.JsonException("expected an array, got " + kind(v));
        List<T> out = new ArrayList<>();
        int i = 0;
        for (Object o : (List<?>) v) {
            try {
                out.add(o == null ? null : item.apply(o));
            } catch (RuntimeException e) {
                throw new Json.JsonException("element " + i + ": " + e.getMessage());
            }
            i++;
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Converts a string value.
     *
     * @param v parsed JSON
     * @return the string
     */
    public static String asString(Object v) {
        if (v instanceof String) return (String) v;
        throw new Json.JsonException("expected a string, got " + kind(v));
    }

    /**
     * Converts a decimal value. The contract sends money as decimal strings; JSON numbers are
     * accepted too. Scale is preserved ({@code "5.00"} stays {@code 5.00}).
     *
     * @param v parsed JSON
     * @return the decimal
     */
    public static BigDecimal asDecimal(Object v) {
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof String) {
            try {
                return new BigDecimal((String) v);
            } catch (NumberFormatException e) {
                throw new Json.JsonException("expected a decimal string, got " + Json.write(v));
            }
        }
        throw new Json.JsonException("expected a decimal, got " + kind(v));
    }

    /**
     * Converts an integer value.
     *
     * @param v parsed JSON
     * @return the integer
     */
    public static Integer asInteger(Object v) {
        if (v instanceof BigDecimal) {
            try {
                return ((BigDecimal) v).intValueExact();
            } catch (ArithmeticException e) {
                throw new Json.JsonException("expected a 32-bit integer, got " + ((BigDecimal) v).toPlainString());
            }
        }
        throw new Json.JsonException("expected an integer, got " + kind(v));
    }

    /**
     * Converts a number (quantities, rates, percentages).
     *
     * @param v parsed JSON
     * @return the number
     */
    public static Double asDouble(Object v) {
        if (v instanceof BigDecimal) return ((BigDecimal) v).doubleValue();
        throw new Json.JsonException("expected a number, got " + kind(v));
    }

    /**
     * Converts a boolean value.
     *
     * @param v parsed JSON
     * @return the boolean
     */
    public static Boolean asBoolean(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        throw new Json.JsonException("expected a boolean, got " + kind(v));
    }

    /**
     * Converts a {@code YYYY-MM-DD} date.
     *
     * @param v parsed JSON
     * @return the date
     */
    public static LocalDate asDate(Object v) {
        String s = asString(v);
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new Json.JsonException("expected a YYYY-MM-DD date, got " + Json.write(s));
        }
    }

    /**
     * Converts an ISO 8601 timestamp, keeping the offset the API reported.
     *
     * @param v parsed JSON
     * @return the timestamp
     */
    public static OffsetDateTime asDateTime(Object v) {
        String s = asString(v);
        try {
            return OffsetDateTime.parse(s, Json.DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new Json.JsonException("expected an ISO 8601 timestamp with offset, got " + Json.write(s));
        }
    }

    /**
     * Converts a free-form object.
     *
     * @param v parsed JSON
     * @return unmodifiable map
     */
    public static Map<String, Object> asMap(Object v) {
        return Collections.unmodifiableMap(object(v, "map"));
    }

    /**
     * Returns an untyped value unchanged (Map, List, String, BigDecimal, Boolean).
     *
     * @param v parsed JSON
     * @return the same value
     */
    public static Object asAny(Object v) {
        return v;
    }

    /**
     * Wire form of a money/decimal field: the plain decimal string with its scale
     * ({@code 5.00} stays {@code "5.00"}), never binary floating point or exponent notation.
     *
     * @param v the decimal, or null
     * @return the decimal string, or null
     */
    public static String decimalText(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    /**
     * Wire form of a list of decimals.
     *
     * @param v the decimals, or null
     * @return the decimal strings, or null
     */
    public static List<String> decimalTexts(List<BigDecimal> v) {
        if (v == null) return null;
        List<String> out = new ArrayList<>(v.size());
        for (BigDecimal d : v) out.add(decimalText(d));
        return out;
    }

    /**
     * Reads back a stored decimal string.
     *
     * @param v a decimal string, or null
     * @return the decimal, or null
     */
    public static BigDecimal decimalOrNull(Object v) {
        return v == null ? null : asDecimal(v);
    }

    /**
     * Reads back a stored list of decimal strings.
     *
     * @param v a list of decimal strings, or null
     * @return the decimals, or null
     */
    public static List<BigDecimal> decimalList(Object v) {
        return v == null ? null : asList(v, Wire::asDecimal);
    }

    static String kind(Object v) {
        if (v == null) return "null";
        if (v instanceof Map) return "an object";
        if (v instanceof List) return "an array";
        if (v instanceof String) return "a string";
        if (v instanceof BigDecimal) return "a number";
        if (v instanceof Boolean) return "a boolean";
        return v.getClass().getSimpleName();
    }
}

package com.desktopaccountingapi.quickbooksdesktop.core;

/**
 * A value with a JSON wire form: every response model, request input and parameter object.
 * {@link Json#write(Object)} serializes it through {@link #toWire()}.
 */
public interface JsonWritable {
    /**
     * The wire form as plain JSON-compatible values: an ordered {@code Map<String, Object>} for
     * objects, with nested models, lists, {@link java.math.BigDecimal}, {@link java.time.LocalDate}
     * and {@link java.time.OffsetDateTime} values that {@link Json#write(Object)} knows how to write.
     *
     * @return the wire form
     */
    Object toWire();

    /**
     * Serializes this value to wire JSON with the SDK's codec (decimals keep their scale, timestamps
     * keep their offset and always include seconds).
     *
     * @return compact JSON text
     */
    default String toJson() {
        return Json.write(toWire());
    }
}

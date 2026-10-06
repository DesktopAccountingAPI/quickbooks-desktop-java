package com.desktopaccountingapi.quickbooksdesktop.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {
    @Test
    void parsesNumbersAsExactDecimals() {
        Object v = Json.parse("{\"a\":52.75,\"b\":5.00,\"c\":-0,\"d\":1e3,\"e\":12345678901234567890.123}");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(new BigDecimal("52.75"), m.get("a"));
        assertEquals("5.00", ((BigDecimal) m.get("b")).toPlainString());
        assertEquals(new BigDecimal("1e3"), m.get("d"));
        assertEquals("12345678901234567890.123", ((BigDecimal) m.get("e")).toPlainString());
    }

    @Test
    void preservesMemberOrderAndNulls() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"z\":1,\"a\":null,\"m\":[true,false,null]}");
        assertEquals(Arrays.asList("z", "a", "m"), Arrays.asList(m.keySet().toArray()));
        assertTrue(m.containsKey("a"));
        assertNull(m.get("a"));
        assertEquals(Arrays.asList(true, false, null), m.get("m"));
    }

    @Test
    void decodesEscapesIncludingSurrogatePairs() {
        assertEquals("a\"b\\c/d\b\f\n\r\t\u00e9\ud83d\ude00", Json.parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\\ud83d\\ude00\""));
        assertEquals("caf\u00e9", Json.parse("\"caf\u00e9\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", " ", "{", "[1,]", "{\"a\":1,}", "01", "1.", ".5", "-", "1e", "+1", "NaN", "tru", "nul",
        "\"unterminated", "\"bad \\x escape\"", "\"\\u12\"", "\"\\ud83d\"", "\"\\ude00\"", "{\"a\" 1}", "[1 2]",
        "1 2", "{a:1}", "'a'", "\"tab\there\"",
    })
    void rejectsInvalidDocuments(String text) {
        assertThrows(Json.JsonException.class, () -> Json.parse(text));
    }

    @Test
    void limitsNesting() {
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < Json.MAX_DEPTH; i++) ok.append('[');
        for (int i = 0; i < Json.MAX_DEPTH; i++) ok.append(']');
        Json.parse(ok.toString());
        assertThrows(Json.JsonException.class, () -> Json.parse("[" + ok + "]"));
    }

    @Test
    void writesStringsWithEscapes() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\\u2028\u00e9\"", Json.write("a\"b\\c\n\u0001\u2028\u00e9"));
    }

    @Test
    void writesNumbersWithoutFloatingPointNoise() {
        assertEquals("5.00", Json.write(new BigDecimal("5.00")));
        assertEquals("100", Json.write(new BigDecimal("1E+2")));
        assertEquals("2", Json.write(2.0));
        assertEquals("0.1", Json.write(0.1));
        assertEquals("1.5", Json.write(1.5f));
        assertEquals("123456789012", Json.write(123456789012L));
        assertThrows(Json.JsonException.class, () -> Json.write(Double.NaN));
        assertThrows(Json.JsonException.class, () -> Json.write(Double.POSITIVE_INFINITY));
    }

    @Test
    void writesDatesAndTimestampsWithSeconds() {
        assertEquals("\"2026-10-05\"", Json.write(LocalDate.of(2026, 10, 5)));
        OffsetDateTime t = OffsetDateTime.of(2026, 10, 5, 9, 14, 0, 0, ZoneOffset.ofHours(-7));
        assertEquals("2026-10-05T09:14-07:00", t.toString()); // what the SDK must not send
        assertEquals("\"2026-10-05T09:14:00-07:00\"", Json.write(t));
        assertEquals("2026-10-05T16:04:01.311Z", Json.formatDateTime(OffsetDateTime.parse("2026-10-05T16:04:01.311Z")));
    }

    @Test
    void roundTripsNestedStructures() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", Arrays.asList(1, "two", null, Arrays.asList()));
        m.put("obj", new LinkedHashMap<>());
        String text = Json.write(m);
        assertEquals("{\"list\":[1,\"two\",null,[]],\"obj\":{}}", text);
        assertEquals(text, Json.write(Json.parse(text)));
    }

    @Test
    void rejectsUnwritableValues() {
        assertThrows(Json.JsonException.class, () -> Json.write(new Object()));
        Map<Object, Object> bad = new LinkedHashMap<>();
        bad.put(1, "x");
        assertThrows(Json.JsonException.class, () -> Json.write(bad));
    }

    @Test
    void wireReadersAreTolerantButTyped() {
        Map<String, Object> o = Wire.object(Json.parse("{\"d\":\"105.50\",\"n\":3,\"t\":\"2026-10-05T09:14:03-07:00\",\"x\":1}"), "T");
        assertEquals("105.50", Wire.get(o, "d", Wire::asDecimal).toPlainString());
        assertEquals(3, Wire.get(o, "n", Wire::asInteger));
        assertEquals(ZoneOffset.ofHours(-7), Wire.get(o, "t", Wire::asDateTime).getOffset());
        assertNull(Wire.get(o, "missing", Wire::asString));
        Json.JsonException e = assertThrows(Json.JsonException.class, () -> Wire.get(o, "n", Wire::asString));
        assertTrue(e.getMessage().contains("\"n\""), e.getMessage());
        List<String> texts = Wire.decimalTexts(Arrays.asList(new BigDecimal("5.00"), null));
        assertEquals(Arrays.asList("5.00", null), texts);
    }
}

package com.desktopaccountingapi.quickbooksdesktop.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, dependency-free JSON codec used by the SDK.
 *
 * <p>Parsing follows RFC 8259: objects become {@link LinkedHashMap} (member order preserved; a
 * repeated member name keeps the last value), arrays become {@link ArrayList}, strings become
 * {@link String}, every number becomes {@link BigDecimal} (no binary floating point, so money keeps
 * its exact digits and scale), and {@code true}/{@code false}/{@code null} become
 * {@link Boolean}/{@code null}. Trailing content, leading zeros, unescaped control characters,
 * lone surrogate escapes and nesting deeper than {@value #MAX_DEPTH} levels are rejected.
 *
 * <p>Writing accepts the parsed types plus {@link JsonWritable} (SDK models and inputs), any
 * {@link Map} with string keys, {@link Iterable}, arrays of objects, other {@link Number} types,
 * {@link LocalDate} ({@code YYYY-MM-DD}) and {@link OffsetDateTime} (ISO 8601, seconds always
 * present, offset preserved). A {@link BigDecimal} is written as a JSON number with
 * {@link BigDecimal#toPlainString()}, so {@code 5.00} stays {@code 5.00}. Money and other decimal
 * fields of SDK models are decimal strings on the wire ({@code "52.75"}); the models convert them
 * before writing.
 */
public final class Json {
    /** Maximum nesting depth of arrays and objects accepted by {@link #parse(String)} and {@link #write(Object)}. */
    public static final int MAX_DEPTH = 512;

    private Json() {}

    /** Thrown when a document is not valid JSON or a value cannot be written as JSON. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates the exception.
         *
         * @param message what is wrong and where
         */
        public JsonException(String message) {
            super(message);
        }
    }

    /**
     * Parses one JSON document.
     *
     * @param text the document
     * @return the parsed value (Map, List, String, BigDecimal, Boolean or null)
     * @throws JsonException if the text is not exactly one valid JSON value
     */
    public static Object parse(String text) {
        if (text == null) throw new JsonException("JSON input is null");
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (p.pos != text.length()) throw p.error("unexpected trailing content");
        return value;
    }

    /**
     * Serializes a value to compact JSON.
     *
     * @param value the value to write
     * @return the JSON text
     * @throws JsonException if the value contains a type that has no JSON form, a non-finite
     *     number, a non-string map key, or nesting deeper than {@value #MAX_DEPTH}
     */
    public static String write(Object value) {
        StringBuilder b = new StringBuilder();
        writeValue(b, value, 0);
        return b.toString();
    }

    /** ISO 8601 with offset; always prints seconds, prints fractional seconds only when non-zero. */
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Formats a timestamp the way the API expects it: seconds always present and the offset kept
     * as given ({@code 2026-10-05T09:14:00-07:00}). {@link OffsetDateTime#toString()} drops
     * {@code :00} seconds, which is why the SDK never uses it on the wire.
     *
     * @param value the timestamp
     * @return the ISO 8601 text
     */
    public static String formatDateTime(OffsetDateTime value) {
        return DATE_TIME.format(value);
    }

    private static void writeValue(StringBuilder b, Object v, int depth) {
        if (depth > MAX_DEPTH) throw new JsonException("value nests deeper than " + MAX_DEPTH + " levels");
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            writeString(b, (String) v);
        } else if (v instanceof Boolean) {
            b.append(((Boolean) v).booleanValue() ? "true" : "false");
        } else if (v instanceof BigDecimal) {
            b.append(((BigDecimal) v).toPlainString());
        } else if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte || v instanceof BigInteger) {
            b.append(v.toString());
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new JsonException("cannot write non-finite number " + d);
            // BigDecimal.valueOf uses the shortest decimal that round-trips the double; plain notation, no exponent.
            BigDecimal dec = v instanceof Float ? new BigDecimal(v.toString()) : BigDecimal.valueOf(d);
            dec = dec.stripTrailingZeros();
            b.append(dec.scale() < 0 ? dec.setScale(0).toPlainString() : dec.toPlainString());
        } else if (v instanceof Number) {
            b.append(new BigDecimal(v.toString()).toPlainString());
        } else if (v instanceof JsonWritable) {
            writeValue(b, ((JsonWritable) v).toWire(), depth);
        } else if (v instanceof LocalDate) {
            writeString(b, v.toString());
        } else if (v instanceof OffsetDateTime) {
            writeString(b, formatDateTime((OffsetDateTime) v));
        } else if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!(e.getKey() instanceof String)) throw new JsonException("JSON object keys must be strings, got " + e.getKey());
                if (!first) b.append(',');
                first = false;
                writeString(b, (String) e.getKey());
                b.append(':');
                writeValue(b, e.getValue(), depth + 1);
            }
            b.append('}');
        } else if (v instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) v) {
                if (!first) b.append(',');
                first = false;
                writeValue(b, item, depth + 1);
            }
            b.append(']');
        } else if (v instanceof Object[]) {
            List<Object> list = new ArrayList<>();
            Collections.addAll(list, (Object[]) v);
            writeValue(b, list, depth);
        } else if (v instanceof Character || v instanceof Enum) {
            writeString(b, v.toString());
        } else {
            throw new JsonException("cannot write " + v.getClass().getName() + " as JSON");
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    static void writeString(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                case '\b':
                    b.append("\\b");
                    break;
                case '\f':
                    b.append("\\f");
                    break;
                default:
                    // Control characters and the JavaScript line separators are escaped; everything else is UTF-8 text.
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        b.append("\\u").append(HEX[(c >> 12) & 0xf]).append(HEX[(c >> 8) & 0xf]).append(HEX[(c >> 4) & 0xf]).append(HEX[c & 0xf]);
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        JsonException error(String message) {
            return new JsonException("invalid JSON at offset " + pos + ": " + message);
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object readValue(int depth) {
            if (pos >= s.length()) throw error("unexpected end of input");
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    if (depth >= MAX_DEPTH) throw error("nesting deeper than " + MAX_DEPTH + " levels");
                    return readObject(depth);
                case '[':
                    if (depth >= MAX_DEPTH) throw error("nesting deeper than " + MAX_DEPTH + " levels");
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    expectWord("true");
                    return Boolean.TRUE;
                case 'f':
                    expectWord("false");
                    return Boolean.FALSE;
                case 'n':
                    expectWord("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                    throw error("unexpected character '" + c + "'");
            }
        }

        private void expectWord(String word) {
            if (!s.startsWith(word, pos)) throw error("expected " + word);
            pos += word.length();
        }

        private Map<String, Object> readObject(int depth) {
            pos++; // {
            Map<String, Object> out = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return out;
            }
            while (true) {
                skipWhitespace();
                if (pos >= s.length() || s.charAt(pos) != '"') throw error("expected a member name");
                String key = readString();
                skipWhitespace();
                if (pos >= s.length() || s.charAt(pos) != ':') throw error("expected ':'");
                pos++;
                skipWhitespace();
                out.put(key, readValue(depth + 1));
                skipWhitespace();
                if (pos >= s.length()) throw error("unterminated object");
                char c = s.charAt(pos++);
                if (c == '}') return out;
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray(int depth) {
            pos++; // [
            List<Object> out = new ArrayList<>();
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return out;
            }
            while (true) {
                skipWhitespace();
                out.add(readValue(depth + 1));
                skipWhitespace();
                if (pos >= s.length()) throw error("unterminated array");
                char c = s.charAt(pos++);
                if (c == ']') return out;
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder b = null;
            int start = pos;
            while (true) {
                if (pos >= s.length()) throw error("unterminated string");
                char c = s.charAt(pos);
                if (c == '"') {
                    String out = b == null ? s.substring(start, pos) : b.append(s, start, pos).toString();
                    pos++;
                    return out;
                }
                if (c < 0x20) throw error("unescaped control character in string");
                if (c != '\\') {
                    pos++;
                    continue;
                }
                if (b == null) b = new StringBuilder();
                b.append(s, start, pos);
                pos++;
                if (pos >= s.length()) throw error("unterminated escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                        b.append('"');
                        break;
                    case '\\':
                        b.append('\\');
                        break;
                    case '/':
                        b.append('/');
                        break;
                    case 'b':
                        b.append('\b');
                        break;
                    case 'f':
                        b.append('\f');
                        break;
                    case 'n':
                        b.append('\n');
                        break;
                    case 'r':
                        b.append('\r');
                        break;
                    case 't':
                        b.append('\t');
                        break;
                    case 'u': {
                        char u = readHex4();
                        if (Character.isHighSurrogate(u)) {
                            if (!s.startsWith("\\u", pos)) throw error("high surrogate escape without a following low surrogate");
                            pos += 2;
                            char low = readHex4();
                            if (!Character.isLowSurrogate(low)) throw error("high surrogate escape without a following low surrogate");
                            b.append(u).append(low);
                        } else if (Character.isLowSurrogate(u)) {
                            throw error("lone low surrogate escape");
                        } else {
                            b.append(u);
                        }
                        break;
                    }
                    default:
                        pos--;
                        throw error("invalid escape '\\" + e + "'");
                }
                start = pos;
            }
        }

        private char readHex4() {
            if (pos + 4 > s.length()) throw error("truncated \\u escape");
            int v = 0;
            for (int i = 0; i < 4; i++) {
                int d = Character.digit(s.charAt(pos + i), 16);
                if (d < 0) throw error("invalid hex digit in \\u escape");
                v = (v << 4) | d;
            }
            pos += 4;
            return (char) v;
        }

        private BigDecimal readNumber() {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            if (pos >= s.length()) throw error("truncated number");
            char c = s.charAt(pos);
            if (c == '0') {
                pos++;
                if (pos < s.length() && isDigit(s.charAt(pos))) throw error("leading zeros are not allowed");
            } else if (c >= '1' && c <= '9') {
                while (pos < s.length() && isDigit(s.charAt(pos))) pos++;
            } else {
                throw error("expected a digit");
            }
            if (pos < s.length() && s.charAt(pos) == '.') {
                pos++;
                if (pos >= s.length() || !isDigit(s.charAt(pos))) throw error("expected a digit after '.'");
                while (pos < s.length() && isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                if (pos >= s.length() || !isDigit(s.charAt(pos))) throw error("expected a digit in the exponent");
                while (pos < s.length() && isDigit(s.charAt(pos))) pos++;
            }
            try {
                return new BigDecimal(s.substring(start, pos));
            } catch (NumberFormatException | ArithmeticException e) {
                throw error("number out of range");
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}

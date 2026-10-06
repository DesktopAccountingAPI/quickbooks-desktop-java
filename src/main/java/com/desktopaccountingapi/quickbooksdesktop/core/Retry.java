package com.desktopaccountingapi.quickbooksdesktop.core;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Random;

/**
 * Retry policy: which responses are retried and how long to wait. Internal to the SDK; exposed for
 * tests and documentation.
 *
 * <ul>
 *   <li>Network errors before a response (reads and writes; writes keep their idempotency key).
 *   <li>{@code 429}, and {@code 5xx} responses whose {@code Daapi-Should-Retry} header is
 *       {@code true}.
 *   <li>Never when {@code Daapi-Should-Retry: false}, or when the error's {@code outcome} is
 *       {@code unknown} or {@code pending}.
 *   <li>Wait {@code Retry-After} when present (seconds or HTTP date, including 0); otherwise 0.5 s
 *       times 2^attempt, capped at 8 s, minus up to 25 % jitter. A {@code Retry-After} longer than
 *       {@value #MAX_RETRY_AFTER_MILLIS} ms is not waited out: the error is thrown instead.
 * </ul>
 */
public final class Retry {
    /** Longest {@code Retry-After} the SDK waits for automatically. */
    public static final long MAX_RETRY_AFTER_MILLIS = 60_000;

    private Retry() {}

    /**
     * Whether an error response may be retried.
     *
     * @param status HTTP status
     * @param shouldRetryHeader value of {@code Daapi-Should-Retry}, or null
     * @param outcome the error's {@code outcome}, or null
     * @return true if the SDK retries it
     */
    public static boolean shouldRetry(int status, String shouldRetryHeader, String outcome) {
        if ("false".equalsIgnoreCase(shouldRetryHeader) || "unknown".equals(outcome) || "pending".equals(outcome)) return false;
        if (status == 429) return true;
        return status >= 500 && "true".equalsIgnoreCase(shouldRetryHeader);
    }

    /**
     * Exponential backoff without {@code Retry-After}.
     *
     * @param attempt 0 for the first retry
     * @param random jitter source
     * @return milliseconds to wait
     */
    public static long backoffMillis(int attempt, Random random) {
        double base = Math.min(8000, 500 * Math.pow(2, Math.min(attempt, 16)));
        return (long) (base * (1 - 0.25 * random.nextDouble()));
    }

    /**
     * Parses {@code Retry-After}.
     *
     * @param header the header value, or null
     * @param now current time, for HTTP-date values
     * @return milliseconds to wait, or null when absent or unparseable
     */
    public static Long retryAfterMillis(String header, Instant now) {
        if (header == null) return null;
        String h = header.trim();
        if (h.isEmpty()) return null;
        try {
            double seconds = Double.parseDouble(h);
            if (seconds < 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) return null;
            return (long) Math.ceil(seconds * 1000);
        } catch (NumberFormatException ignored) {
            // Not delta-seconds; try an HTTP date.
        }
        try {
            Instant at = ZonedDateTime.parse(h, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(0, at.toEpochMilli() - now.toEpochMilli());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

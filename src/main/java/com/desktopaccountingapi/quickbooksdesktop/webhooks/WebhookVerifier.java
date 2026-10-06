package com.desktopaccountingapi.quickbooksdesktop.webhooks;

import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Standard Webhooks signatures ({@code webhook-id}, {@code webhook-timestamp},
 * {@code webhook-signature}: {@code v1,<base64 HMAC-SHA256 of "id.timestamp.body">}). Immutable and
 * thread-safe. Use {@link Webhooks} for the defaults, or configure the tolerance and clock here.
 *
 * <pre>{@code
 * WebhookVerifier verifier = WebhookVerifier.builder().tolerance(Duration.ofMinutes(5)).build();
 * WebhookEvent event = verifier.verify(rawBody, requestHeaders, System.getenv("DAAPI_WEBHOOK_SECRET"));
 * }</pre>
 *
 * <p>Pass the raw request body exactly as received; re-serialized JSON does not match the
 * signature.
 */
public final class WebhookVerifier {
    /** Default tolerance between the {@code webhook-timestamp} and the local clock, in both directions. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private static final WebhookVerifier DEFAULT = builder().build();

    private final Duration tolerance;
    private final Clock clock;

    private WebhookVerifier(Builder b) {
        this.tolerance = b.tolerance;
        this.clock = b.clock;
    }

    /**
     * Verifier with a 5 minute tolerance and the system clock.
     *
     * @return the shared default verifier
     */
    public static WebhookVerifier defaults() {
        return DEFAULT;
    }

    /**
     * Starts a builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Verifies the signature and timestamp, then parses the event.
     *
     * @param payload the raw request body
     * @param headers request headers; names are matched case-insensitively, values may be
     *     {@code String} or a {@code List} of strings
     * @param secret the endpoint's signing secret, with or without the {@code whsec_} prefix
     * @return the event
     * @throws WebhookVerificationException if verification fails or the body is not an event
     */
    public WebhookEvent verify(String payload, Map<String, ?> headers, String secret) {
        verifySignature(payload, headers, secret);
        return WebhookEvent.parse(payload);
    }

    /**
     * Verifies only the signature and timestamp, without parsing the body.
     *
     * @param payload the raw request body
     * @param headers request headers (case-insensitive names)
     * @param secret the signing secret, with or without {@code whsec_}
     * @throws WebhookVerificationException if verification fails
     */
    public void verifySignature(String payload, Map<String, ?> headers, String secret) {
        if (payload == null) throw new WebhookVerificationException("payload is null");
        if (headers == null) throw new WebhookVerificationException("headers are null");
        String id = header(headers, "webhook-id");
        String timestamp = header(headers, "webhook-timestamp");
        String signatures = header(headers, "webhook-signature");
        if (id == null || id.isEmpty()) throw new WebhookVerificationException("missing webhook-id header");
        if (timestamp == null || timestamp.isEmpty()) throw new WebhookVerificationException("missing webhook-timestamp header");
        if (signatures == null || signatures.trim().isEmpty()) throw new WebhookVerificationException("missing webhook-signature header");
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new WebhookVerificationException("webhook-timestamp is not a Unix time in seconds");
        }
        long now = clock.millis() / 1000;
        if (Math.abs(now - ts) > tolerance.getSeconds()) {
            throw new WebhookVerificationException("webhook-timestamp is outside the " + tolerance.getSeconds() + " s tolerance (check the server clock, or this is a replay)");
        }
        byte[] expected = Webhooks.hmac(Webhooks.secretBytes(secret), id + "." + timestamp.trim() + "." + payload);
        for (String part : signatures.trim().split(" +")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !"v1".equals(part.substring(0, comma))) continue;
            byte[] given;
            try {
                given = Base64.getDecoder().decode(part.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) return;
        }
        throw new WebhookVerificationException("no matching v1 signature in webhook-signature");
    }

    private static String header(Map<String, ?> headers, String name) {
        for (Map.Entry<String, ?> e : headers.entrySet()) {
            if (e.getKey() == null || !e.getKey().toLowerCase(Locale.ROOT).equals(name)) continue;
            Object v = e.getValue();
            if (v instanceof String) return (String) v;
            if (v instanceof List) {
                StringBuilder b = new StringBuilder();
                for (Object o : (List<?>) v) {
                    if (o == null) continue;
                    if (b.length() > 0) b.append(' ');
                    b.append(o);
                }
                return b.toString();
            }
            return v == null ? null : v.toString();
        }
        return null;
    }

    static Mac mac(byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Builder for {@link WebhookVerifier}. */
    public static final class Builder {
        private Duration tolerance = DEFAULT_TOLERANCE;
        private Clock clock = Clock.systemUTC();

        private Builder() {}

        /**
         * Maximum difference between {@code webhook-timestamp} and the clock, in either direction.
         *
         * @param tolerance non-negative duration (default 5 minutes)
         * @return this builder
         */
        public Builder tolerance(Duration tolerance) {
            if (tolerance == null || tolerance.isNegative()) throw new IllegalArgumentException("tolerance must be zero or positive");
            this.tolerance = tolerance;
            return this;
        }

        /**
         * Clock used for the timestamp check (inject a fixed clock in tests).
         *
         * @param clock the clock
         * @return this builder
         */
        public Builder clock(Clock clock) {
            if (clock == null) throw new IllegalArgumentException("clock is null");
            this.clock = clock;
            return this;
        }

        /**
         * Builds the verifier.
         *
         * @return the verifier
         */
        public WebhookVerifier build() {
            return new WebhookVerifier(this);
        }
    }
}

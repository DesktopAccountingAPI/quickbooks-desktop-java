package com.desktopaccountingapi.quickbooksdesktop.webhooks;

import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import java.util.Base64;
import java.util.Map;

/**
 * Webhook signature helpers with the default settings (5 minute tolerance, system clock). They need
 * no API key or client.
 *
 * <pre>{@code
 * try {
 *     WebhookEvent event = Webhooks.verify(rawBody, headers, System.getenv("DAAPI_WEBHOOK_SECRET"));
 *     if (WebhookEventType.REQUEST_SUCCEEDED.equals(event.type())) { ... }
 * } catch (WebhookVerificationException e) {
 *     // respond 400
 * }
 * }</pre>
 */
public final class Webhooks {
    private Webhooks() {}

    /**
     * Verifies a delivery and parses the event. See {@link WebhookVerifier#verify}.
     *
     * @param payload the raw request body
     * @param headers request headers (case-insensitive names; String or List values)
     * @param secret the signing secret, with or without {@code whsec_}
     * @return the event
     * @throws WebhookVerificationException if verification fails
     */
    public static WebhookEvent verify(String payload, Map<String, ?> headers, String secret) {
        return WebhookVerifier.defaults().verify(payload, headers, secret);
    }

    /**
     * Verifies only the signature and timestamp. See {@link WebhookVerifier#verifySignature}.
     *
     * @param payload the raw request body
     * @param headers request headers (case-insensitive names)
     * @param secret the signing secret, with or without {@code whsec_}
     * @throws WebhookVerificationException if verification fails
     */
    public static void verifySignature(String payload, Map<String, ?> headers, String secret) {
        WebhookVerifier.defaults().verifySignature(payload, headers, secret);
    }

    /**
     * Computes a {@code webhook-signature} value ({@code v1,<base64>}). Useful to test a receiver
     * without a real delivery.
     *
     * @param id the {@code webhook-id}
     * @param timestamp the {@code webhook-timestamp} (Unix seconds)
     * @param payload the body
     * @param secret the signing secret, with or without {@code whsec_}
     * @return the signature header value
     */
    public static String sign(String id, long timestamp, String payload, String secret) {
        return "v1," + Base64.getEncoder().encodeToString(hmac(secretBytes(secret), id + "." + timestamp + "." + payload));
    }

    static byte[] secretBytes(String secret) {
        if (secret == null || secret.isEmpty()) throw new WebhookVerificationException("webhook secret is empty");
        String s = secret.startsWith("whsec_") ? secret.substring(6) : secret;
        try {
            byte[] key = Base64.getDecoder().decode(s);
            if (key.length == 0) throw new WebhookVerificationException("webhook secret is empty");
            return key;
        } catch (IllegalArgumentException e) {
            throw new WebhookVerificationException("webhook secret is not valid base64 after the whsec_ prefix");
        }
    }

    static byte[] hmac(byte[] key, String message) {
        return WebhookVerifier.mac(key).doFinal(WebhookVerifier.utf8(message));
    }
}

package com.desktopaccountingapi.quickbooksdesktop.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhooksTest {
    static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    static final String OTHER = "whsec_Y29uZm9ybWFuY2Utd2ViaG9vay1zZWNyZXQtMzJieXQ=";
    static final String BODY = "{\"id\":\"evt_1\",\"type\":\"request.succeeded\",\"timestamp\":\"2026-10-05T16:04:01.311Z\",\"projectId\":\"proj_1\",\"data\":{\"id\":\"req_1\",\"status\":\"succeeded\"}}";
    static final long NOW = 1_791_216_241L;

    static WebhookVerifier at(long now) {
        return WebhookVerifier.builder().clock(Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC)).build();
    }

    static Map<String, Object> headers(String id, long ts, Object signature) {
        Map<String, Object> h = new HashMap<>();
        h.put("Webhook-Id", id);
        h.put("WEBHOOK-TIMESTAMP", Long.toString(ts));
        h.put("webhook-signature", signature);
        return h;
    }

    @Test
    void standardWebhooksReferenceVector() {
        Map<String, Object> h = headers("msg_p5jXN8AQM9LWM0D4loKWxJek", 1614265330, "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
        at(1614265330).verifySignature("{\"test\": 2432232314}", h, SECRET);
        assertEquals("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=", Webhooks.sign("msg_p5jXN8AQM9LWM0D4loKWxJek", 1614265330, "{\"test\": 2432232314}", SECRET));
        // The secret works without its prefix too.
        at(1614265330).verifySignature("{\"test\": 2432232314}", h, SECRET.substring(6));
    }

    @Test
    void verifiesAndParsesEvent() {
        String sig = Webhooks.sign("evt_1", NOW, BODY, SECRET);
        WebhookEvent e = at(NOW).verify(BODY, headers("evt_1", NOW, sig), SECRET);
        assertEquals("evt_1", e.id());
        assertEquals(WebhookEventType.REQUEST_SUCCEEDED, e.type());
        assertEquals("succeeded", e.data().get("status"));
        assertEquals(Instant.parse("2026-10-05T16:04:01.311Z"), e.timestamp().toInstant());
    }

    @Test
    void acceptsAnyOfSeveralSignaturesDuringRotation() {
        String good = Webhooks.sign("evt_1", NOW, BODY, SECRET);
        String old = Webhooks.sign("evt_1", NOW, BODY, OTHER);
        at(NOW).verify(BODY, headers("evt_1", NOW, old + " v2,abc " + good), SECRET);
        at(NOW).verify(BODY, headers("evt_1", NOW, Arrays.asList(old, good)), SECRET);
    }

    @Test
    void rejectsTamperingWrongSecretAndStaleTimestamps() {
        String sig = Webhooks.sign("evt_1", NOW, BODY, SECRET);
        assertThrows(WebhookVerificationException.class, () -> at(NOW).verify(BODY.replace("succeeded", "failed"), headers("evt_1", NOW, sig), SECRET));
        assertThrows(WebhookVerificationException.class, () -> at(NOW).verify(BODY, headers("evt_1", NOW, sig), OTHER));
        assertThrows(WebhookVerificationException.class, () -> at(NOW + 301).verify(BODY, headers("evt_1", NOW, sig), SECRET));
        assertThrows(WebhookVerificationException.class, () -> at(NOW - 301).verify(BODY, headers("evt_1", NOW, sig), SECRET));
        at(NOW + 300).verify(BODY, headers("evt_1", NOW, sig), SECRET);
        WebhookVerifier lenient = WebhookVerifier.builder().tolerance(Duration.ofMinutes(10)).clock(Clock.fixed(Instant.ofEpochSecond(NOW + 400), ZoneOffset.UTC)).build();
        lenient.verify(BODY, headers("evt_1", NOW, sig), SECRET);
        assertThrows(WebhookVerificationException.class, () -> at(NOW).verify(BODY, headers("evt_1", NOW, sig.replace("v1,", "v2,")), SECRET));
        assertThrows(WebhookVerificationException.class, () -> at(NOW).verify(BODY, headers("evt_1", NOW, ""), SECRET));
        assertThrows(WebhookVerificationException.class, () -> at(NOW).verify(BODY, headers("evt_1", NOW, sig), "whsec_!!!"));
        assertThrows(WebhookVerificationException.class, () -> at(NOW).verify("not json", headers("evt_1", NOW, Webhooks.sign("evt_1", NOW, "not json", SECRET)), SECRET));
    }
}

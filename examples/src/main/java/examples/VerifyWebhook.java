package examples;

import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.WebhookEvent;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.Webhooks;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Signs a sample event with DAAPI_WEBHOOK_SECRET and verifies it, the way a webhook receiver
 * verifies a real delivery. Needs no API key.
 *
 * <pre>DAAPI_WEBHOOK_SECRET=whsec_... mvn -q compile exec:java -Dexec.mainClass=examples.VerifyWebhook</pre>
 */
public final class VerifyWebhook {
    private VerifyWebhook() {}

    public static void main(String[] args) {
        String secret = System.getenv("DAAPI_WEBHOOK_SECRET");
        if (secret == null) {
            System.err.println("Set DAAPI_WEBHOOK_SECRET (whsec_...)");
            System.exit(2);
        }
        String body = "{\"id\":\"evt_example\",\"type\":\"request.succeeded\",\"timestamp\":\"2026-10-05T16:04:01.311Z\","
            + "\"projectId\":\"proj_example\",\"data\":{\"objectType\":\"request\",\"id\":\"req_example\",\"status\":\"succeeded\"}}";
        long now = Instant.now().getEpochSecond();
        Map<String, String> headers = new HashMap<>();
        headers.put("webhook-id", "evt_example");
        headers.put("webhook-timestamp", Long.toString(now));
        headers.put("webhook-signature", Webhooks.sign("evt_example", now, body, secret));
        try {
            WebhookEvent event = Webhooks.verify(body, headers, secret);
            System.out.println("verified " + event.type() + " for " + event.data().get("id"));
        } catch (WebhookVerificationException e) {
            System.err.println("rejected: " + e.getMessage());
            System.exit(1);
        }
    }
}

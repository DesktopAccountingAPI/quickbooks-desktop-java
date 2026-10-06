// Harness for the README's webhook handler body: a request handler that returns an HTTP status.
import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import com.desktopaccountingapi.quickbooksdesktop.models.Request;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.WebhookEvent;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.WebhookEventType;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.Webhooks;
import java.util.Map;

final class {{NAME}} {
    private {{NAME}}() {}

    static int handle(DesktopAccountingApiClient client, String rawBody, Map<String, ?> requestHeaders) {
        {{SAMPLE}}
    }
}

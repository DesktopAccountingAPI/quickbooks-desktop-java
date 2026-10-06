package examples;

import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import com.desktopaccountingapi.quickbooksdesktop.core.Page;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiException;
import com.desktopaccountingapi.quickbooksdesktop.models.HealthCheck;
import com.desktopaccountingapi.quickbooksdesktop.models.Invoice;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceListParams;

/**
 * Health check of the end user's QuickBooks Desktop connection, then the first 10 invoices.
 *
 * <p>Environment: DAAPI_SECRET_KEY, DAAPI_END_USER_ID, optional DAAPI_BASE_URL.
 *
 * <pre>mvn -q compile exec:java -Dexec.mainClass=examples.Quickstart</pre>
 */
public final class Quickstart {
    private Quickstart() {}

    public static void main(String[] args) {
        DesktopAccountingApiClient client = DesktopAccountingApiClient.fromEnv().forEndUser(System.getenv("DAAPI_END_USER_ID"));
        try {
            HealthCheck health = client.qbd().healthCheck();
            System.out.println("QuickBooks Desktop: " + health.status() + " in " + health.duration() + " ms");
            Page<Invoice> page = client.qbd().invoices().list(new InvoiceListParams().limit(10)).firstPage();
            for (Invoice invoice : page.data()) {
                System.out.printf("%s  %s  %s  %s%n", invoice.id(), invoice.refNumber(), invoice.transactionDate(), invoice.subtotal());
            }
            System.out.println(page.data().size() + " invoices" + (page.hasMore() ? " (more available)" : ""));
        } catch (ApiException e) {
            System.err.println(e.code() + ": " + e.userFacingMessage() + " (" + e.requestId() + ")");
            System.exit(1);
        }
    }
}

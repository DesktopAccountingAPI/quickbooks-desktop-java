// Harness for README fragments (scripts/readme-samples.mjs): each fragment becomes the body of
// {{NAME}}.run, with these names in scope. Not part of the Maven build.
import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import com.desktopaccountingapi.quickbooksdesktop.core.*;
import com.desktopaccountingapi.quickbooksdesktop.errors.*;
import com.desktopaccountingapi.quickbooksdesktop.models.*;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class {{NAME}} {
    private {{NAME}}() {}

    static void process(Object value) {}

    static void showToEndUser(String message) {}

    static void run(DesktopAccountingApiClient client) {
        {{SAMPLE}}
    }
}

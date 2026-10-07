package com.desktopaccountingapi.quickbooksdesktop.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PagerTest {
    private static final String KEY = "sk_test_Conformance0Key0For0SDK0Tests000010nQFLR";

    private static Transport.Response page(String json) {
        return new Transport.Response(200, Headers.ofSingle(Map.of("Content-Type", "application/json")), json);
    }

    @Test
    void slowConsumerGetsTheNextPageInTheBackground() throws InterruptedException {
        List<Transport.Request> requests = Collections.synchronizedList(new ArrayList<>());
        List<Transport.Response> replies = Collections.synchronizedList(new ArrayList<>(Arrays.asList(
            page("{\"data\":[{\"id\":\"1\"},{\"id\":\"2\"}],\"nextCursor\":\"c2\",\"hasMore\":true}"),
            page("{\"data\":[{\"id\":\"3\"}],\"nextCursor\":null,\"hasMore\":false}"))));
        Transport transport = request -> {
            requests.add(request);
            return replies.remove(0);
        };
        long saved = Pager.readAheadAfterNanos;
        Pager.readAheadAfterNanos = 0;
        try {
            DesktopAccountingApiClient client = DesktopAccountingApiClient.builder().apiKey(KEY).baseUrl("https://api.test").endUserId("eu_1").transport(transport).build();
            List<String> ids = new ArrayList<>();
            for (var invoice : client.qbd().invoices().list()) {
                ids.add(invoice.id());
                if (invoice.id().equals("1")) {
                    for (int i = 0; i < 100 && requests.size() < 2; i++) Thread.sleep(10);
                    assertEquals(2, requests.size(), "page 2 was requested while page 1 was still being consumed");
                }
            }
            assertEquals(Arrays.asList("1", "2", "3"), ids);
            assertEquals(2, requests.size());
        } finally {
            Pager.readAheadAfterNanos = saved;
        }
    }
}

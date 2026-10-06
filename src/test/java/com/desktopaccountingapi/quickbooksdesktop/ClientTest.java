package com.desktopaccountingapi.quickbooksdesktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.desktopaccountingapi.quickbooksdesktop.core.Headers;
import com.desktopaccountingapi.quickbooksdesktop.core.RequestOptions;
import com.desktopaccountingapi.quickbooksdesktop.core.Retry;
import com.desktopaccountingapi.quickbooksdesktop.core.SdkInfo;
import com.desktopaccountingapi.quickbooksdesktop.core.Transport;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiConnectionException;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiException;
import com.desktopaccountingapi.quickbooksdesktop.errors.CursorExpiredException;
import com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException;
import com.desktopaccountingapi.quickbooksdesktop.errors.IntegrationConnectionException;
import com.desktopaccountingapi.quickbooksdesktop.errors.InvalidRequestException;
import com.desktopaccountingapi.quickbooksdesktop.models.ErrorCode;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceCreateInput;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceLineCreateInput;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceListParams;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceUpdateInput;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ClientTest {
    static final String KEY = "sk_test_Conformance0Key0For0SDK0Tests000010nQFLR";
    static final String EU = "eu_01j9x4m6v4c8k2t7q0r5s3w1zb";

    /** Records requests and replays canned responses (or throws IOException for "drop"). */
    static final class Stub implements Transport {
        final List<Transport.Request> requests = Collections.synchronizedList(new ArrayList<>());
        final List<Object> replies;

        Stub(Object... replies) {
            this.replies = new ArrayList<>(Arrays.asList(replies));
        }

        @Override
        public Response send(Request request) throws IOException {
            requests.add(request);
            Object r = replies.isEmpty() ? null : replies.remove(0);
            if (r instanceof IOException) throw (IOException) r;
            if (r == null) throw new AssertionError("unexpected request " + request.method() + " " + request.uri());
            return (Response) r;
        }
    }

    static Transport.Response json(int status, String body, String... headers) {
        java.util.Map<String, String> h = new java.util.LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) h.put(headers[i], headers[i + 1]);
        return new Transport.Response(status, Headers.ofSingle(h), body);
    }

    static String error(String type, String code, String outcome) {
        return "{\"error\":{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"message\":\"m\",\"userFacingMessage\":\"u\",\"httpStatusCode\":503,"
            + "\"integrationCode\":\"0x80040414\",\"requestId\":\"req_1\",\"cause\":\"c\",\"fixes\":[{\"actor\":\"end_user\",\"action\":\"close it\"}],"
            + "\"docsUrl\":\"https://www.desktopaccountingapi.com/docs/errors/#x\",\"retryable\":true,\"outcome\":\"" + outcome + "\",\"param\":null,\"details\":{\"k\":1}}}";
    }

    static DesktopAccountingApiClient client(Stub stub, String endUser) {
        DesktopAccountingApiClient.Builder b = DesktopAccountingApiClient.builder().apiKey(KEY).baseUrl("https://api.test/base/").transport(stub);
        if (endUser != null) b.endUserId(endUser);
        return b.build();
    }

    static final String CUSTOMER = "{\"id\":\"80000001-1700000000\",\"objectType\":\"qbd_customer\",\"balance\":\"5.00\",\"newField\":{\"x\":1}}";

    @Test
    void sendsDocumentedHeadersOnly() {
        Stub stub = new Stub(json(200, CUSTOMER, "Daapi-Request-Id", "req_x"));
        client(stub, EU).qbd().customers().retrieve("80000001-1700000000");
        Transport.Request r = stub.requests.get(0);
        assertEquals("GET", r.method());
        assertEquals("https://api.test/base/v1/quickbooks-desktop/customers/80000001-1700000000", r.uri().toString());
        assertEquals("Bearer " + KEY, r.headers().get("Authorization"));
        assertEquals(EU, r.headers().get("Daapi-End-User-Id"));
        assertEquals(SdkInfo.USER_AGENT, r.headers().get("User-Agent"));
        assertFalse(r.headers().containsKey("Idempotency-Key"));
        for (String h : r.headers().keySet()) {
            if (h.toLowerCase().startsWith("daapi-")) assertEquals("Daapi-End-User-Id", h, "invented Daapi header " + h);
        }
    }

    @Test
    void parsesDecimalsAndKeepsUnknownFields() {
        Stub stub = new Stub(json(200, CUSTOMER));
        var customer = client(stub, EU).qbd().customers().retrieve("80000001-1700000000");
        assertEquals("5.00", customer.balance().toPlainString());
        assertEquals(Collections.singletonMap("x", BigDecimal.ONE), customer.additionalProperties().get("newField"));
        assertTrue(customer.toJson().contains("\"balance\":\"5.00\""), customer.toJson());
        assertTrue(customer.toJson().contains("\"newField\":{\"x\":1}"), customer.toJson());
    }

    @Test
    void qbdOperationWithoutEndUserFailsLocally() {
        Stub stub = new Stub();
        DaapiException e = assertThrows(DaapiException.class, () -> client(stub, null).qbd().healthCheck());
        assertTrue(e.getMessage().contains("forEndUser"), e.getMessage());
        assertTrue(stub.requests.isEmpty());
        // forEndUser and per-call options supply it.
        Stub stub2 = new Stub(json(200, "{\"status\":\"ok\",\"duration\":1}"), json(200, "{\"status\":\"ok\",\"duration\":1}"));
        DesktopAccountingApiClient c = client(stub2, null);
        c.forEndUser(EU).qbd().healthCheck();
        c.qbd().healthCheck(RequestOptions.endUser("eu_other"));
        assertEquals(EU, stub2.requests.get(0).headers().get("Daapi-End-User-Id"));
        assertEquals("eu_other", stub2.requests.get(1).headers().get("Daapi-End-User-Id"));
        assertSame(c.options().transport(), c.forEndUser(EU).options().transport());
    }

    @Test
    void platformOperationsNeverSendEndUser() {
        Stub stub = new Stub(json(200, "{\"objectType\":\"list\",\"url\":\"/v1/end-users\",\"data\":[],\"nextCursor\":null,\"hasMore\":false}"));
        client(stub, EU).endUsers().list().firstPage();
        assertNull(stub.requests.get(0).headers().get("Daapi-End-User-Id"));
    }

    @Test
    void writeReusesIdempotencyKeyAcrossRetries() {
        Stub stub = new Stub(new IOException("reset"), json(503, error("INTEGRATION_CONNECTION_ERROR", "QBD_MODAL_DIALOG_OPEN", "not_applied"), "Daapi-Should-Retry", "true", "Retry-After", "0"),
            json(201, "{\"id\":\"7-1700000000\",\"subtotal\":\"105.50\"}"));
        InvoiceCreateInput input = new InvoiceCreateInput("80000001-1700000000")
            .transactionDate(LocalDate.of(2026, 10, 5))
            .lines(Arrays.asList(new InvoiceLineCreateInput().itemId("80000005-1700000000").quantity(2).rate(new BigDecimal("52.75"))));
        var invoice = client(stub, EU).qbd().invoices().create(input);
        assertEquals("105.50", invoice.subtotal().toPlainString());
        assertEquals(3, stub.requests.size());
        String key = stub.requests.get(0).headers().get("Idempotency-Key");
        assertTrue(key.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), key);
        for (Transport.Request r : stub.requests) assertEquals(key, r.headers().get("Idempotency-Key"));
        assertEquals("{\"customerId\":\"80000001-1700000000\",\"transactionDate\":\"2026-10-05\",\"lines\":[{\"itemId\":\"80000005-1700000000\",\"quantity\":2,\"rate\":\"52.75\"}]}",
            stub.requests.get(0).body());
        assertTrue(stub.requests.get(0).headers().get("Content-Type").startsWith("application/json"));
    }

    @Test
    void neverRetriesUnknownOrPendingOutcomes() {
        Stub stub = new Stub(json(502, error("OUTCOME_UNKNOWN_ERROR", "QBD_WRITE_OUTCOME_UNKNOWN", "unknown"), "Daapi-Should-Retry", "true"));
        assertThrows(ApiException.class, () -> client(stub, EU).qbd().invoices().voidTransaction("7-1700000000"));
        assertEquals(1, stub.requests.size());
    }

    @Test
    void mapsErrorFields() {
        Stub stub = new Stub(json(503, error("INTEGRATION_CONNECTION_ERROR", "QBD_MODAL_DIALOG_OPEN", "not_applied"), "Daapi-Should-Retry", "false"));
        IntegrationConnectionException e = assertThrows(IntegrationConnectionException.class, () -> client(stub, EU).qbd().healthCheck());
        assertEquals(503, e.status());
        assertEquals(ErrorCode.QBD_MODAL_DIALOG_OPEN, e.code());
        assertEquals("m", e.getMessage());
        assertEquals("u", e.userFacingMessage());
        assertEquals("c", e.errorCause());
        assertEquals("end_user", e.fixes().get(0).actor());
        assertEquals("req_1", e.requestId());
        assertEquals("0x80040414", e.integrationCode());
        assertEquals(Boolean.TRUE, e.retryable());
        assertEquals("not_applied", e.outcome());
        assertEquals(BigDecimal.ONE, e.details().get("k"));
        assertEquals("false", e.headers().get("daapi-should-retry"));
    }

    @Test
    void nonJsonErrorIsBaseApiExceptionWithHeaderRequestId() {
        Stub stub = new Stub(new Transport.Response(502, Headers.ofSingle(Collections.singletonMap("Daapi-Request-Id", "req_h")), "<html>bad gateway</html>"));
        ApiException e = assertThrows(ApiException.class, () -> client(stub, EU).qbd().healthCheck());
        assertEquals(ApiException.class, e.getClass());
        assertEquals(502, e.status());
        assertEquals("req_h", e.requestId());
        assertNull(e.code());
    }

    @Test
    void cursorExpiredIsInvalidRequest() {
        String body = error("INVALID_REQUEST_ERROR", "CURSOR_EXPIRED", "not_applicable").replace("{\"k\":1}", "{\"reason\":\"idle_timeout\",\"pagesServed\":4}");
        Stub stub = new Stub(json(410, body, "Daapi-Should-Retry", "false"));
        CursorExpiredException e = assertThrows(CursorExpiredException.class,
            () -> client(stub, EU).qbd().invoices().list(new InvoiceListParams().limit(2)).firstPage());
        assertTrue(e instanceof InvalidRequestException);
        assertEquals("idle_timeout", e.reason());
        assertEquals(4, e.pagesServed());
    }

    @Test
    void connectionErrorAfterRetries() {
        Stub stub = new Stub(new IOException("a"), new IOException("b"));
        DesktopAccountingApiClient c = DesktopAccountingApiClient.builder().apiKey(KEY).baseUrl("https://api.test").endUserId(EU).maxRetries(1).transport(stub).build();
        assertThrows(ApiConnectionException.class, () -> c.qbd().healthCheck());
        assertEquals(2, stub.requests.size());
    }

    @Test
    void queryUsesRepeatedKeysAndPlainValues() {
        Stub stub = new Stub(json(200, "{\"objectType\":\"list\",\"url\":\"/v1/quickbooks-desktop/invoices\",\"data\":[],\"nextCursor\":null,\"hasMore\":false}"));
        client(stub, EU).qbd().invoices().list(new InvoiceListParams()
            .customerIds(Arrays.asList("a b", "c&d"))
            .limit(10)
            .updatedAfter(OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.ofHours(-7)))
            .transactionDateFrom(LocalDate.of(2026, 1, 2))).firstPage();
        assertEquals("customerIds=a%20b&customerIds=c%26d&limit=10&updatedAfter=2026-10-05T09%3A00%3A00-07%3A00&transactionDateFrom=2026-01-02",
            stub.requests.get(0).uri().getRawQuery());
    }

    @Test
    void inputsDistinguishOmittedFromNull() {
        InvoiceUpdateInput u = new InvoiceUpdateInput("1700000007").memo(null).customerId("c").customerId(null);
        assertEquals("{\"revisionNumber\":\"1700000007\",\"memo\":null}", u.toJson());
        assertTrue(u.isSet("memo"));
        assertFalse(u.isSet("customerId"));
        DaapiException e = assertThrows(DaapiException.class, () -> new InvoiceCreateInput().memo("x").validate());
        assertTrue(e.getMessage().contains("customerId"), e.getMessage());
        assertEquals(new BigDecimal("5.00"), new InvoiceLineCreateInput().rate(new BigDecimal("5.00")).rate());
    }

    @Test
    void retryPolicy() {
        assertTrue(Retry.shouldRetry(429, null, null));
        assertTrue(Retry.shouldRetry(503, "true", "not_applied"));
        assertFalse(Retry.shouldRetry(503, null, null));
        assertFalse(Retry.shouldRetry(503, "false", null));
        assertFalse(Retry.shouldRetry(429, "false", null));
        assertFalse(Retry.shouldRetry(500, "true", "pending"));
        assertFalse(Retry.shouldRetry(502, "true", "unknown"));
        assertFalse(Retry.shouldRetry(400, "true", null));
        Instant now = Instant.parse("2026-10-05T16:00:00Z");
        assertEquals(0L, Retry.retryAfterMillis("0", now));
        assertEquals(1500L, Retry.retryAfterMillis("1.5", now));
        assertEquals(30_000L, Retry.retryAfterMillis("Mon, 05 Oct 2026 16:00:30 GMT", now));
        assertNull(Retry.retryAfterMillis("soon", now));
        assertNull(Retry.retryAfterMillis("-1", now));
        Random r = new Random(1);
        for (int attempt = 0; attempt < 10; attempt++) {
            long ms = Retry.backoffMillis(attempt, r);
            double base = Math.min(8000, 500 * Math.pow(2, attempt));
            assertTrue(ms <= base && ms >= base * 0.75, attempt + ": " + ms);
        }
    }

    @Test
    void missingKeyNamesTheEnvironmentVariable() {
        DaapiException e = assertThrows(DaapiException.class, () -> DesktopAccountingApiClient.builder().apiKey("").build());
        assertTrue(e.getMessage().contains("DAAPI_SECRET_KEY"));
        assertThrows(DaapiException.class, () -> DesktopAccountingApiClient.builder().apiKey(KEY).baseUrl("ftp://x").build());
        assertFalse(client(new Stub(), EU).options().toString().contains(KEY));
    }

    @Test
    void loggerNeverSeesSecrets() {
        List<String> lines = new ArrayList<>();
        Stub stub = new Stub(json(200, CUSTOMER, "Daapi-Request-Id", "req_x"));
        DesktopAccountingApiClient c = DesktopAccountingApiClient.builder().apiKey(KEY).baseUrl("https://api.test").endUserId(EU).transport(stub).logger(lines::add).build();
        c.qbd().customers().retrieve("80000001-1700000000");
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("-> 200 req_x"), lines.get(0));
        for (String l : lines) assertFalse(l.contains(KEY) || l.contains("Bearer") || l.contains("balance"));
    }
}

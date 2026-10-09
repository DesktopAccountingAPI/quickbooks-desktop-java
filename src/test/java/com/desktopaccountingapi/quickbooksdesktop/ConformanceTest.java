package com.desktopaccountingapi.quickbooksdesktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.desktopaccountingapi.quickbooksdesktop.core.ApiKeys;
import com.desktopaccountingapi.quickbooksdesktop.core.ApiResponse;
import com.desktopaccountingapi.quickbooksdesktop.core.InputObject;
import com.desktopaccountingapi.quickbooksdesktop.core.Json;
import com.desktopaccountingapi.quickbooksdesktop.core.JsonWritable;
import com.desktopaccountingapi.quickbooksdesktop.core.Page;
import com.desktopaccountingapi.quickbooksdesktop.core.Pager;
import com.desktopaccountingapi.quickbooksdesktop.core.RequestHandle;
import com.desktopaccountingapi.quickbooksdesktop.core.RequestOptions;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiConnectionException;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiException;
import com.desktopaccountingapi.quickbooksdesktop.errors.AuthenticationException;
import com.desktopaccountingapi.quickbooksdesktop.errors.BillingException;
import com.desktopaccountingapi.quickbooksdesktop.errors.CursorExpiredException;
import com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException;
import com.desktopaccountingapi.quickbooksdesktop.errors.IntegrationConnectionException;
import com.desktopaccountingapi.quickbooksdesktop.errors.IntegrationException;
import com.desktopaccountingapi.quickbooksdesktop.errors.InternalException;
import com.desktopaccountingapi.quickbooksdesktop.errors.InvalidRequestException;
import com.desktopaccountingapi.quickbooksdesktop.errors.OutcomeUnknownException;
import com.desktopaccountingapi.quickbooksdesktop.errors.PermissionException;
import com.desktopaccountingapi.quickbooksdesktop.errors.RateLimitException;
import com.desktopaccountingapi.quickbooksdesktop.errors.RequestPendingException;
import com.desktopaccountingapi.quickbooksdesktop.errors.WebhookVerificationException;
import com.desktopaccountingapi.quickbooksdesktop.models.PassthroughInput;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.WebhookEvent;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.WebhookVerifier;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the shared cross-language conformance suite (conformance/README.md) through the SDK's real
 * HTTP stack against conformance/mock-server.mjs.
 */
class ConformanceTest {
    private static final Path FIXTURES = Paths.get("conformance", "fixtures");
    private static final Map<String, Class<? extends Throwable>> CLASSES = new HashMap<>();

    static {
        CLASSES.put("DaapiError", DaapiException.class);
        CLASSES.put("ApiError", ApiException.class);
        CLASSES.put("InvalidRequestError", InvalidRequestException.class);
        CLASSES.put("AuthenticationError", AuthenticationException.class);
        CLASSES.put("PermissionError", PermissionException.class);
        CLASSES.put("BillingError", BillingException.class);
        CLASSES.put("RateLimitError", RateLimitException.class);
        CLASSES.put("IntegrationConnectionError", IntegrationConnectionException.class);
        CLASSES.put("IntegrationError", IntegrationException.class);
        CLASSES.put("OutcomeUnknownError", OutcomeUnknownException.class);
        CLASSES.put("InternalError", InternalException.class);
        CLASSES.put("CursorExpiredError", CursorExpiredException.class);
        CLASSES.put("RequestPendingError", RequestPendingException.class);
        CLASSES.put("ApiConnectionError", ApiConnectionException.class);
    }

    private static Process server;
    private static String serverUrl;
    private static final HttpClient CONTROL = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeAll
    static void startServer() throws IOException {
        String node = System.getenv("NODE") != null ? System.getenv("NODE") : "node";
        server = new ProcessBuilder(node, Paths.get("conformance", "mock-server.mjs").toString(), "--exit-on-stdin-close")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        BufferedReader out = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
        String line = out.readLine();
        if (line == null || !line.startsWith("MOCK_SERVER_URL=")) throw new IllegalStateException("mock server did not start: " + line);
        serverUrl = line.substring("MOCK_SERVER_URL=".length()).trim();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) {
            server.getOutputStream().close();
            server.destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixture(String name) throws IOException {
        return (Map<String, Object>) Json.parse(new String(Files.readAllBytes(FIXTURES.resolve(name)), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o == null ? Collections.emptyMap() : (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o == null ? Collections.emptyList() : (List<Object>) o;
    }

    // ------------------------------------------------------------------ scenarios

    @TestFactory
    Stream<DynamicTest> scenarios() throws IOException {
        Map<String, Object> f = fixture("scenarios.json");
        List<Object> scenarios = list(f.get("scenarios"));
        assertTrue(scenarios.size() > 0, "no scenarios");
        return scenarios.stream().map(s -> DynamicTest.dynamicTest(String.valueOf(map(s).get("name")), () -> runScenario(f, map(s))));
    }

    private static String control(String method, String path) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(serverUrl + path)).timeout(Duration.ofSeconds(10));
        b.method(method, HttpRequest.BodyPublishers.noBody());
        return CONTROL.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    /** What a scenario call produced. */
    private static final class Outcome {
        Object result;
        String text;
        List<Object> items = new ArrayList<>();
        Page<?> page;
        RequestHandle<?> handle;
        ApiResponse<?> response;
        Throwable error;
    }

    private static void runScenario(Map<String, Object> fixtures, Map<String, Object> sc) throws Exception {
        String name = (String) sc.get("name");
        // Scenarios limited to other SDKs (Conductor-named options that exist only there).
        if (sc.get("only") != null && !list(sc.get("only")).contains("java")) return;
        control("POST", "/_control/reset/" + name);
        Map<String, Object> defaults = map(fixtures.get("defaultClient"));
        Map<String, Object> cc = map(sc.get("client"));
        Map<String, Object> call = map(sc.get("call"));
        Outcome out = new Outcome();
        try {
            DesktopAccountingApiClient.Builder b = DesktopAccountingApiClient.builder()
                .apiKey((String) (cc.containsKey("apiKey") ? cc.get("apiKey") : fixtures.get("apiKey")))
                .baseUrl(serverUrl + "/s/" + name + (cc.get("baseUrlSuffix") != null ? (String) cc.get("baseUrlSuffix") : ""));
            for (Map.Entry<String, Object> h : map(cc.get("defaultHeaders")).entrySet()) b.defaultHeader(h.getKey(), (String) h.getValue());
            if (cc.get("totalTimeoutMs") != null) b.totalTimeout(Duration.ofMillis(((BigDecimal) cc.get("totalTimeoutMs")).longValueExact()));
            Object endUser = cc.containsKey("endUserId") ? cc.get("endUserId") : defaults.get("endUserId");
            if (endUser != null) b.endUserId((String) endUser);
            Object retries = cc.containsKey("maxRetries") ? cc.get("maxRetries") : defaults.get("maxRetries");
            if (retries != null) b.maxRetries(((BigDecimal) retries).intValueExact());
            Object timeout = cc.containsKey("timeoutMs") ? cc.get("timeoutMs") : defaults.get("timeoutMs");
            if (timeout != null) b.timeout(Duration.ofMillis(((BigDecimal) timeout).longValueExact()));
            DesktopAccountingApiClient client = b.build();
            invoke(client, call, out);
        } catch (Throwable t) {
            out.error = t;
        }
        Map<String, Object> verify = map(Json.parse(control("GET", "/_control/verify/" + name)));
        assertEquals(Boolean.TRUE, verify.get("ok"), () -> name + ": mock server verification failed: " + verify.get("errors") + (out.error != null ? " (call raised " + out.error + ")" : ""));
        check(name, map(sc.get("outcome")), out);
    }

    private static RequestOptions options(Map<String, Object> o) {
        RequestOptions.Builder b = RequestOptions.builder();
        if (o.get("idempotencyKey") != null) b.idempotencyKey((String) o.get("idempotencyKey"));
        if (o.get("endUserId") != null) b.endUserId((String) o.get("endUserId"));
        if (o.get("timeoutMs") != null) b.timeout(Duration.ofMillis(((BigDecimal) o.get("timeoutMs")).longValueExact()));
        if (o.get("serverTimeoutSeconds") != null) b.serverTimeout(Duration.ofSeconds(((BigDecimal) o.get("serverTimeoutSeconds")).longValueExact()));
        if (o.get("maxRetries") != null) b.maxRetries(((BigDecimal) o.get("maxRetries")).intValueExact());
        return b.build();
    }

    /** Java method name for an operation's last segment (config methodRenames.java). */
    private static String methodName(String verb) {
        return "void".equals(verb) ? "voidTransaction" : verb;
    }

    private static void invoke(DesktopAccountingApiClient client, Map<String, Object> call, Outcome out) throws Exception {
        String op = (String) call.get("op");
        String kind = (String) call.get("kind");
        String[] parts = op.split("\\.");
        Object target = client;
        for (int i = 0; i < parts.length - 1; i++) target = target.getClass().getMethod(parts[i]).invoke(target);
        if ("enqueue".equals(kind)) target = target.getClass().getMethod("enqueue").invoke(target);
        String name = methodName(parts[parts.length - 1]) + ("withResponse".equals(kind) ? "WithResponse" : "xml".equals(kind) ? "Xml" : "");
        Method m = null;
        for (Method c : target.getClass().getMethods()) {
            if (!c.getName().equals(name)) continue;
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 0 || p[p.length - 1] != RequestOptions.class) continue;
            if (m == null || p.length > m.getParameterCount()) m = c;
        }
        assertNotNull(m, "no method " + name + " on " + target.getClass().getSimpleName());
        Map<String, Object> path = map(call.get("path"));
        List<Object> args = new ArrayList<>(path.values());
        Class<?>[] types = m.getParameterTypes();
        if ("xml".equals(kind)) {
            args.add(call.get("xml"));
        } else if (types.length == args.size() + 2) {
            Map<String, Object> params = map(call.get("params"));
            args.add(build(types[args.size()], params));
        } else if (call.get("params") != null) {
            fail(op + " takes no parameter object, but the scenario has params");
        }
        args.add(options(map(call.get("options"))));
        Object result;
        try {
            result = m.invoke(target, args.toArray());
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
        switch (kind) {
            case "call":
                out.result = result;
                break;
            case "xml":
                out.text = (String) result;
                break;
            case "withResponse":
                out.response = (ApiResponse<?>) result;
                out.result = out.response.data();
                break;
            case "firstPage":
                out.page = ((Pager<?>) result).firstPage();
                break;
            case "iterate": {
                Object take = call.get("take");
                for (Object item : (Pager<?>) result) {
                    out.items.add(item);
                    if (take != null && out.items.size() >= ((BigDecimal) take).intValueExact()) break;
                }
                break;
            }
            case "enqueue": {
                out.handle = (RequestHandle<?>) result;
                long ms = ((BigDecimal) map(call.get("wait")).getOrDefault("timeoutMs", BigDecimal.valueOf(20000))).longValueExact();
                out.result = out.handle.await(Duration.ofMillis(ms));
                break;
            }
            default:
                fail("unknown call kind " + kind);
        }
    }

    private static Exception unwrap(InvocationTargetException e) {
        Throwable t = e.getCause();
        if (t instanceof RuntimeException) return (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        return new IllegalStateException(t);
    }

    /** Builds a typed parameter object through its public fluent setters. */
    private static Object build(Class<?> type, Map<String, Object> params) throws Exception {
        if (type == PassthroughInput.class) return PassthroughInput.of(params);
        Object o = type.getConstructor().newInstance();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String setter = "class".equals(e.getKey()) ? "classRef" : e.getKey();
            // Fluent setters; when overloaded (number: Double/double, date-or-date-time:
            // String/LocalDate/OffsetDateTime) use the boxed or String form.
            Method chosen = null;
            for (Method c : type.getMethods()) {
                if (!c.getName().equals(setter) || c.getParameterCount() != 1 || c.getReturnType() != type) continue;
                Class<?> p = c.getParameterTypes()[0];
                if (p.isPrimitive()) continue;
                if (chosen == null || p == String.class) chosen = c;
            }
            assertNotNull(chosen, "no setter " + setter + " on " + type.getSimpleName());
            chosen.invoke(o, convert(chosen.getGenericParameterTypes()[0], e.getValue()));
        }
        return o;
    }

    private static Object convert(Type t, Object v) throws Exception {
        if (v == null) return null;
        if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) t;
            if (pt.getRawType() == List.class) {
                List<Object> out = new ArrayList<>();
                for (Object item : list(v)) out.add(convert(pt.getActualTypeArguments()[0], item));
                return out;
            }
            return v; // Map<String, Object>
        }
        Class<?> c = (Class<?>) t;
        if (c == String.class || c == Object.class || c == Boolean.class) return v;
        if (c == BigDecimal.class) {
            assertTrue(v instanceof String, "decimal fixture values are strings");
            return new BigDecimal((String) v);
        }
        if (c == Integer.class) return ((BigDecimal) v).intValueExact();
        if (c == Double.class) return ((BigDecimal) v).doubleValue();
        if (c == LocalDate.class) return LocalDate.parse((String) v);
        if (c == OffsetDateTime.class) return OffsetDateTime.parse((String) v);
        if (InputObject.class.isAssignableFrom(c)) return build(c, map(v));
        throw new IllegalArgumentException("cannot convert fixture value to " + c);
    }

    // ------------------------------------------------------------------ outcome checks

    private static Object wire(Object value) {
        // Serialize with the SDK's own codec, then read back as generic JSON.
        return Json.parse(Json.write(value instanceof JsonWritable ? ((JsonWritable) value).toWire() : value));
    }

    private static Object at(Object json, String dotted) {
        Object cur = json;
        for (String part : dotted.split("\\.")) {
            if (!(cur instanceof Map)) return Missing.INSTANCE;
            Map<String, Object> m = map(cur);
            if (!m.containsKey(part)) return Missing.INSTANCE;
            cur = m.get(part);
        }
        return cur;
    }

    private enum Missing {
        INSTANCE
    }

    private static boolean jsonEquals(Object a, Object b) {
        if (a instanceof BigDecimal && b instanceof BigDecimal) return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
        if (a instanceof Map && b instanceof Map) {
            Map<String, Object> ma = map(a);
            Map<String, Object> mb = map(b);
            if (!ma.keySet().equals(mb.keySet())) return false;
            for (String k : ma.keySet()) if (!jsonEquals(ma.get(k), mb.get(k))) return false;
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<Object> la = list(a);
            List<Object> lb = list(b);
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) if (!jsonEquals(la.get(i), lb.get(i))) return false;
            return true;
        }
        return Objects.equals(a, b);
    }

    private static void expectJson(String what, Object expected, Object actual) {
        assertTrue(jsonEquals(expected, actual), () -> what + ": expected " + Json.write(expected) + ", got " + (actual == Missing.INSTANCE ? "(missing)" : Json.write(actual)));
    }

    private static List<Object> ids(List<?> items) {
        return items.stream().map(i -> at(wire(i), "id")).collect(Collectors.toList());
    }

    private static void check(String name, Map<String, Object> expected, Outcome out) {
        Map<String, Object> err = map(expected.get("error"));
        if (!expected.containsKey("error") && out.error != null) {
            throw new AssertionError(name + ": unexpected " + out.error, out.error);
        }
        if (expected.containsKey("result")) {
            assertNotNull(out.result, name + ": no result" + (out.error != null ? " (raised " + out.error + ")" : ""));
            Object w = wire(out.result);
            for (Map.Entry<String, Object> e : map(expected.get("result")).entrySet()) expectJson(name + " result." + e.getKey(), e.getValue(), at(w, e.getKey()));
        }
        if (expected.containsKey("text")) assertEquals(expected.get("text"), out.text, name + " text");
        if (expected.containsKey("items")) expectJson(name + " items", expected.get("items"), ids(out.items));
        if (expected.containsKey("page")) {
            Map<String, Object> p = map(expected.get("page"));
            assertNotNull(out.page, name + ": no page");
            if (p.containsKey("ids")) expectJson(name + " page.ids", p.get("ids"), ids(out.page.data()));
            if (p.containsKey("nextCursor")) assertEquals(p.get("nextCursor"), out.page.nextCursor(), name + " page.nextCursor");
            if (p.containsKey("hasMore")) assertEquals(p.get("hasMore"), out.page.hasMore(), name + " page.hasMore");
            if (p.containsKey("remainingCount")) expectJson(name + " page.remainingCount", p.get("remainingCount"), wire(out.page.remainingCount()));
        }
        if (expected.containsKey("handle")) {
            Map<String, Object> hd = map(expected.get("handle"));
            assertNotNull(out.handle, name + ": no handle" + (out.error != null ? " (raised " + out.error + ")" : ""));
            if (hd.containsKey("id")) assertEquals(hd.get("id"), out.handle.id(), name + " handle.id");
            if (hd.containsKey("status")) assertEquals(hd.get("status"), out.handle.request().status(), name + " handle.status");
        }
        if (expected.containsKey("response")) {
            Map<String, Object> r = map(expected.get("response"));
            assertNotNull(out.response, name + ": no response");
            if (r.containsKey("status")) assertEquals(((BigDecimal) r.get("status")).intValueExact(), out.response.statusCode(), name + " response.status");
            if (r.containsKey("requestId")) assertEquals(r.get("requestId"), out.response.requestId(), name + " response.requestId");
            for (Map.Entry<String, Object> e : map(r.get("headers")).entrySet()) assertEquals(e.getValue(), out.response.headers().get(e.getKey()), name + " header " + e.getKey());
        }
        if (expected.containsKey("error")) checkError(name, err, out.error);
    }

    private static void checkError(String name, Map<String, Object> err, Throwable t) {
        assertNotNull(t, name + ": expected an error, the call succeeded");
        String canonical = (String) err.get("class");
        Class<? extends Throwable> cls = CLASSES.get(canonical);
        assertNotNull(cls, "unknown canonical class " + canonical);
        if ("ApiError".equals(canonical)) assertEquals(ApiException.class, t.getClass(), () -> name + ": expected exactly ApiException, got " + t);
        else assertTrue(cls.isInstance(t), () -> name + ": expected " + cls.getSimpleName() + ", got " + t);
        Map<String, Object> actual = new LinkedHashMap<>();
        if (t instanceof ApiException) {
            ApiException e = (ApiException) t;
            actual.put("type", e.type());
            actual.put("code", e.code());
            actual.put("status", e.status());
            actual.put("message", e.getMessage());
            actual.put("userFacingMessage", e.userFacingMessage());
            actual.put("requestId", e.requestId());
            actual.put("param", e.param());
            actual.put("retryable", e.retryable());
            actual.put("outcome", e.outcome());
            actual.put("integrationCode", e.integrationCode());
            actual.put("cause", e.errorCause());
            actual.put("docsUrl", e.docsUrl());
            List<Object> fixes = new ArrayList<>();
            for (ApiException.Fix fx : e.fixes()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("actor", fx.actor());
                m.put("action", fx.action());
                fixes.add(m);
            }
            actual.put("fixes", fixes);
        }
        if (t instanceof CursorExpiredException) {
            CursorExpiredException c = (CursorExpiredException) t;
            actual.put("itemsYielded", c.itemsYielded());
            actual.put("pagesServed", c.pagesServed());
            actual.put("lastId", c.lastId());
            actual.put("lastUpdatedAt", c.lastUpdatedAt());
        }
        if (t instanceof RequestPendingException) {
            RequestPendingException p = (RequestPendingException) t;
            actual.put("requestId", p.requestId());
            actual.put("timeoutErrorCode", p.timeoutError() == null ? null : p.timeoutError().code());
        }
        if (t instanceof DaapiException) {
            String key = ((DaapiException) t).idempotencyKey();
            boolean uuid = key != null && key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            actual.put("idempotencyKey", "$uuid".equals(err.get("idempotencyKey")) && uuid ? "$uuid" : key);
        }
        for (Map.Entry<String, Object> e : err.entrySet()) {
            String k = e.getKey();
            if ("class".equals(k)) continue;
            if ("details".equals(k)) {
                assertTrue(t instanceof ApiException, name + ": details on a non-API error");
                Map<String, Object> details = map(wire(((ApiException) t).details()));
                for (Map.Entry<String, Object> d : map(e.getValue()).entrySet()) expectJson(name + " error.details." + d.getKey(), d.getValue(), details.containsKey(d.getKey()) ? details.get(d.getKey()) : Missing.INSTANCE);
                continue;
            }
            assertTrue(actual.containsKey(k), () -> name + ": error field " + k + " is not exposed by " + t.getClass().getSimpleName());
            expectJson(name + " error." + k, e.getValue(), wire(actual.get(k)));
        }
    }

    // ------------------------------------------------------------------ webhooks and keys

    @TestFactory
    Stream<DynamicTest> webhookVectors() throws IOException {
        Map<String, Object> f = fixture("webhooks.json");
        return list(f.get("cases")).stream().map(c -> DynamicTest.dynamicTest(String.valueOf(map(c).get("name")), () -> {
            Map<String, Object> v = map(c);
            long now = ((BigDecimal) v.get("now")).longValueExact();
            WebhookVerifier verifier = WebhookVerifier.builder().clock(Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC)).build();
            String secret = (String) (v.get("secret") != null ? v.get("secret") : f.get("secret"));
            Map<String, Object> headers = map(v.get("headers"));
            String body = (String) v.get("body");
            boolean signatureOnly = Boolean.TRUE.equals(v.get("signatureOnly"));
            if (!Boolean.TRUE.equals(v.get("valid"))) {
                assertThrows(WebhookVerificationException.class, () -> {
                    if (signatureOnly) verifier.verifySignature(body, headers, secret);
                    else verifier.verify(body, headers, secret);
                });
                return;
            }
            if (signatureOnly) {
                verifier.verifySignature(body, headers, secret);
                return;
            }
            WebhookEvent event = verifier.verify(body, headers, secret);
            Map<String, Object> ev = map(v.get("event"));
            assertEquals(ev.get("id"), event.id());
            assertEquals(ev.get("type"), event.type());
            assertEquals(ev.get("timestamp"), Json.formatDateTime(event.timestamp()));
            assertEquals(ev.get("projectId"), event.projectId());
            if (ev.containsKey("dataStatus")) assertEquals(ev.get("dataStatus"), event.data().get("status"));
            if (ev.containsKey("dataId")) assertEquals(ev.get("dataId"), event.data().get("id"));
        }));
    }

    @TestFactory
    Stream<DynamicTest> apiKeyVectors() throws IOException {
        Map<String, Object> f = fixture("api-keys.json");
        Stream<DynamicTest> valid = list(f.get("valid")).stream().map(k -> DynamicTest.dynamicTest("valid " + k, () -> {
            assertTrue(ApiKeys.isValid((String) k));
            assertNotNull(DesktopAccountingApiClient.builder().apiKey((String) k).baseUrl("http://127.0.0.1:9").build());
        }));
        Stream<DynamicTest> invalid = list(f.get("invalid")).stream().map(x -> DynamicTest.dynamicTest("invalid: " + map(x).get("reason"), () -> {
            String key = (String) map(x).get("key");
            assertTrue(!ApiKeys.isValid(key));
            // Rejected while building the client, before any request; the transport must never run.
            DaapiException e = assertThrows(DaapiException.class, () -> DesktopAccountingApiClient.builder().apiKey(key).baseUrl("http://127.0.0.1:9")
                .transport(r -> {
                    throw new AssertionError("request sent with an invalid key");
                }).build());
            assertTrue(e.getMessage().contains("DAAPI_SECRET_KEY"), e.getMessage());
            assertTrue(key.isEmpty() || !e.getMessage().contains(key), "error message echoes the key");
        }));
        return Stream.concat(valid, invalid);
    }
}

package com.desktopaccountingapi.quickbooksdesktop.core;

import com.desktopaccountingapi.quickbooksdesktop.errors.ApiConnectionException;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiErrorInfo;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiException;
import com.desktopaccountingapi.quickbooksdesktop.errors.ApiTimeoutException;
import com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException;
import com.desktopaccountingapi.quickbooksdesktop.errors.RequestPendingException;
import com.desktopaccountingapi.quickbooksdesktop.models.Request;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * The SDK's HTTP engine: headers, idempotency keys, retries, error mapping, long-polling after a
 * server timeout, async mode and pagination. Generated services call it; it is internal to the SDK
 * and not a stable API.
 */
public final class ClientCore {
    private static final OperationSpec REQUESTS_RETRIEVE = OperationSpec.of("requests.retrieve", "GET", 0);
    private static final SecureRandom JITTER = new SecureRandom();
    private static final ExecutorService READ_AHEAD = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "daapi-read-ahead");
        t.setDaemon(true);
        return t;
    });

    private final ClientOptions options;

    /**
     * Creates the engine.
     *
     * @param options resolved settings
     */
    public ClientCore(ClientOptions options) {
        this.options = options;
    }

    /**
     * The client settings.
     *
     * @return the settings
     */
    public ClientOptions options() {
        return options;
    }

    /**
     * Copy with another default end user, sharing the transport.
     *
     * @param endUserId the end user
     * @return the copy
     */
    public ClientCore withEndUserId(String endUserId) {
        if (endUserId == null || endUserId.isEmpty()) throw new IllegalArgumentException("endUserId is required");
        return new ClientCore(options.withEndUserId(endUserId));
    }

    static ExecutorService readAhead() {
        return READ_AHEAD;
    }

    /**
     * Percent-encodes one path parameter.
     *
     * @param name parameter name, for the error message
     * @param value the value
     * @return the encoded segment
     */
    public static String pathParam(String name, String value) {
        if (value == null || value.isEmpty()) throw new DaapiException(name + " is required");
        return encode(value);
    }

    // ---------------------------------------------------------------- typed entry points

    /**
     * Runs a synchronous call and parses the result.
     *
     * @param op operation
     * @param path request path with encoded parameters
     * @param query query parameters, or null
     * @param body JSON body, or null
     * @param o per-call options
     * @param parse result parser
     * @param <T> result type
     * @return the result
     */
    public <T> T call(OperationSpec op, String path, InputObject query, InputObject body, RequestOptions o, Function<Object, T> parse) {
        return callWithResponse(op, path, query, body, o, parse).data();
    }

    /**
     * Runs a synchronous call and returns the result with status and headers.
     *
     * @param op operation
     * @param path request path with encoded parameters
     * @param query query parameters, or null
     * @param body JSON body, or null
     * @param o per-call options
     * @param parse result parser
     * @param <T> result type
     * @return the result and response metadata
     */
    public <T> ApiResponse<T> callWithResponse(OperationSpec op, String path, InputObject query, InputObject body, RequestOptions o, Function<Object, T> parse) {
        Call c = new Call(op, o);
        String json = body == null ? null : validated(body).toJson();
        if (query != null) query.validate();
        try {
            Transport.Response res;
            try {
                res = c.send(path, query, json, "application/json", "application/json", false);
            } catch (ApiException e) {
                String pending = pendingRequestId(e);
                if (pending == null) throw e;
                log("request " + pending + " still running after a server timeout; polling until the call deadline");
                Polled polled = poll(pending, c.deadlineNanos, c.o, e, c.idempotencyKey);
                return new ApiResponse<>(parseResult(op, polled.result, parse), polled.status, polled.headers, c.idempotencyKey);
            }
            return new ApiResponse<>(parseResult(op, parseJson(op, res.body()), parse), res.status(), res.headers(), c.idempotencyKey);
        } catch (DaapiException e) {
            // Every exception of a write carries the key it was sent with.
            e.attachIdempotencyKey(c.idempotencyKey);
            throw e;
        }
    }

    /**
     * Runs a call whose request and response bodies are XML text (passthrough).
     *
     * @param op operation
     * @param path request path with encoded parameters
     * @param xml request body
     * @param o per-call options
     * @return the response body
     */
    public String callXml(OperationSpec op, String path, String xml, RequestOptions o) {
        if (xml == null) throw new DaapiException("xml is required");
        Call c = new Call(op, o);
        try {
            try {
                return c.send(path, null, xml, "application/xml", "application/xml", false).body();
            } catch (ApiException e) {
                String pending = pendingRequestId(e);
                if (pending == null) throw e;
                Object result = poll(pending, c.deadlineNanos, c.o, e, c.idempotencyKey).result;
                return result instanceof String ? (String) result : Json.write(result);
            }
        } catch (DaapiException e) {
            e.attachIdempotencyKey(c.idempotencyKey);
            throw e;
        }
    }

    /**
     * Sends a call in async mode ({@code Prefer: respond-async}).
     *
     * @param op operation
     * @param path request path with encoded parameters
     * @param query query parameters, or null
     * @param body JSON body, or null
     * @param o per-call options
     * @param parse parser for the eventual result
     * @param <T> result type
     * @return a handle on the queued request
     */
    public <T> RequestHandle<T> enqueue(OperationSpec op, String path, InputObject query, InputObject body, RequestOptions o, Function<Object, T> parse) {
        if (!op.has(OperationSpec.ASYNC)) throw new DaapiException(op.operationId() + " does not support async mode");
        Call c = new Call(op, o);
        String json = body == null ? null : validated(body).toJson();
        if (query != null) query.validate();
        Transport.Response res;
        Object parsed;
        try {
            res = c.send(path, query, json, "application/json", "application/json", true);
            parsed = parseJson(op, res.body());
        } catch (DaapiException e) {
            e.attachIdempotencyKey(c.idempotencyKey);
            throw e;
        }
        if (res.status() == 202) {
            Request request = parseRequest(parsed);
            return new RequestHandle<>(this, op, request.id(), request, parse, c.o, c.idempotencyKey);
        }
        // The API answered with the final result right away.
        return RequestHandle.completed(this, op, res.headers().get("Daapi-Request-Id"), parseResult(op, parsed, parse), c.o);
    }

    /**
     * Starts a cursor list.
     *
     * @param op operation
     * @param path request path
     * @param query first-page parameters, or null
     * @param o per-call options
     * @param item parser for one list item
     * @param <T> item type
     * @return a lazy pager; no request is sent until it is used
     */
    public <T> Pager<T> paginate(OperationSpec op, String path, InputObject query, RequestOptions o, Function<Object, T> item) {
        if (query != null) query.validate();
        return new Pager<>(this, op, path, query, o == null ? RequestOptions.NONE : o, item);
    }

    <T> Page<T> fetchPage(OperationSpec op, String path, InputObject query, RequestOptions o, Function<Object, T> item) {
        Transport.Response res = new Call(op, o).send(path, query, null, null, "application/json", false);
        Object parsed = parseJson(op, res.body());
        try {
            return Page.fromJson(parsed, item, res.headers().get("Daapi-Request-Id"));
        } catch (RuntimeException e) {
            throw new DaapiException("Could not parse the " + op.operationId() + " response: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- request resources

    /** Result of reading a request resource. */
    static final class Polled {
        final Object raw;
        final Object result;
        final int status;
        final Headers headers;
        final boolean done;

        Polled(Object raw, Object result, int status, Headers headers, boolean done) {
            this.raw = raw;
            this.result = result;
            this.status = status;
            this.headers = headers;
            this.done = done;
        }
    }

    /**
     * Reads a request resource once.
     *
     * @param id request ID
     * @param waitSeconds long-poll seconds (0 to 60), or null for no wait
     * @param o per-call options
     * @return the raw request, its result when succeeded, and the response metadata
     * @throws ApiException when the request ended in failure
     */
    Polled readRequest(String id, Integer waitSeconds, RequestOptions o) {
        Transport.Response res = fetchRequest(id, waitSeconds, o, null);
        Object raw = parseJson(REQUESTS_RETRIEVE, res.body());
        return interpret(id, raw, res);
    }

    /**
     * One {@code GET /v1/requests/{id}}. A long poll with {@code deadlineNanos} keeps every
     * attempt, retry and backoff inside the caller's deadline (codex review #15).
     */
    private Transport.Response fetchRequest(String id, Integer waitSeconds, RequestOptions o, Long deadlineNanos) {
        RequestOptions ro = o == null ? RequestOptions.NONE : o;
        if (waitSeconds != null) {
            // Each long-poll attempt may take up to waitSeconds; give it 10 s on top.
            ro = ro.toBuilder().timeout(Duration.ofSeconds(waitSeconds + 10L)).build();
        }
        InputObject q = waitSeconds == null ? null : new WaitQuery(waitSeconds);
        return new Call(REQUESTS_RETRIEVE, ro, waitSeconds == null, deadlineNanos).send("/v1/requests/" + pathParam("id", id), q, null, null, "application/json", false);
    }

    /**
     * Reads a request resource once without interpreting its status.
     *
     * @param id request ID
     * @param o per-call options
     * @return the parsed JSON
     */
    Object readRequestAnyStatus(String id, RequestOptions o) {
        Transport.Response res = new Call(REQUESTS_RETRIEVE, o).send("/v1/requests/" + pathParam("id", id), null, null, null, "application/json", false);
        return parseJson(REQUESTS_RETRIEVE, res.body());
    }

    private Polled interpret(String id, Object raw, Transport.Response res) {
        Map<String, Object> m = Wire.object(raw, "Request");
        String status = m.get("status") instanceof String ? (String) m.get("status") : "";
        switch (status) {
            case "succeeded":
                // QuickBooks answered, but the API could not map the answer (for example
                // QBD_RESPONSE_UNREADABLE, outcome applied): throw that catalog error (codex review #4).
                if (m.get("error") instanceof Map) throw errorFromRequest(id, status, m.get("error"));
                if (Boolean.TRUE.equals(m.get("resultExpired"))) {
                    throw new DaapiException("Request " + id + " succeeded, but its result is no longer stored (resultExpired).");
                }
                return new Polled(raw, m.get("result"), res.status(), res.headers(), true);
            case "failed":
            case "canceled":
            case "outcome_unknown":
                throw errorFromRequest(id, status, m.get("error"));
            default:
                return new Polled(raw, null, res.status(), res.headers(), false);
        }
    }

    /**
     * Long-polls a request until it finishes or the deadline passes. A finished request returns its
     * result or throws its own typed exception. Anything else that ends the wait (the deadline, or a
     * poll that failed: 429, 5xx, 404, network, timeout) throws {@link RequestPendingException}: the
     * failed poll's own retryable exception would invite a duplicate write (Fable review F-1).
     *
     * @param id request ID
     * @param deadlineNanos {@link System#nanoTime()} deadline
     * @param o per-call options
     * @param timeoutError the 504 that started the wait, or null
     * @param idempotencyKey the write's key, or null
     * @return the finished request
     */
    Polled poll(String id, long deadlineNanos, RequestOptions o, ApiException timeoutError, String idempotencyKey) {
        Request last = null;
        while (true) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) throw new RequestPendingException(id, last, timeoutError, null, idempotencyKey);
            int wait = (int) Math.min(60, Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L));
            Transport.Response res;
            Object raw;
            Request current;
            try {
                res = fetchRequest(id, wait, o, deadlineNanos);
                raw = parseJson(REQUESTS_RETRIEVE, res.body());
                current = parseRequest(raw);
            } catch (DaapiException e) {
                throw new RequestPendingException(id, last, timeoutError, e, idempotencyKey);
            }
            // The default transport stops its timer before reading the body, so a slow body can end
            // after the deadline: a late answer is not returned, settled or not (codex re-review #15).
            if (System.nanoTime() - deadlineNanos > 0) throw new RequestPendingException(id, current, timeoutError, null, idempotencyKey);
            Polled p = interpret(id, raw, res);
            if (p.done) return p;
            last = current;
        }
    }

    static Request parseRequest(Object raw) {
        try {
            return Request.fromJson(raw);
        } catch (RuntimeException e) {
            throw new DaapiException("Could not parse the request resource: " + e.getMessage(), e);
        }
    }

    private static ApiException errorFromRequest(String id, String status, Object error) {
        Map<String, Object> e = new LinkedHashMap<>();
        if (error instanceof Map) {
            for (Map.Entry<?, ?> x : ((Map<?, ?>) error).entrySet()) e.put(String.valueOf(x.getKey()), x.getValue());
        } else if ("outcome_unknown".equals(status)) {
            e.put("type", "OUTCOME_UNKNOWN_ERROR");
            e.put("outcome", "unknown");
        }
        Object http = e.get("httpStatusCode");
        Integer code = http instanceof Number ? ((Number) http).intValue() : null;
        return ApiException.create(ApiErrorInfo.of(code, e, Headers.empty(), null, "Request " + id + " ended with status " + status + "."));
    }

    private static String pendingRequestId(ApiException e) {
        if (!"QBD_REQUEST_TIMEOUT".equals(e.code())) return null;
        Object id = e.details().get("requestId");
        return id instanceof String && !((String) id).isEmpty() ? (String) id : null;
    }

    // ---------------------------------------------------------------- helpers

    private static InputObject validated(InputObject body) {
        body.validate();
        return body;
    }

    private static Object parseJson(OperationSpec op, String body) {
        try {
            return Json.parse(body);
        } catch (Json.JsonException e) {
            throw new DaapiException("The " + op.operationId() + " response is not valid JSON: " + e.getMessage(), e);
        }
    }

    static <T> T parseResult(OperationSpec op, Object json, Function<Object, T> parse) {
        try {
            return parse.apply(json);
        } catch (DaapiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DaapiException("Could not parse the " + op.operationId() + " response: " + e.getMessage(), e);
        }
    }

    void log(String line) {
        RequestLogger l = options.logger;
        if (l == null) return;
        try {
            l.log(line);
        } catch (RuntimeException ignored) {
            // A failing logger never breaks a request.
        }
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static String encode(String s) {
        StringBuilder b = new StringBuilder();
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
            int c = x & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') {
                b.append((char) c);
            } else {
                b.append('%').append(HEX[c >> 4]).append(HEX[c & 0xf]);
            }
        }
        return b.toString();
    }

    static String queryString(InputObject query) {
        if (query == null) return "";
        List<Map.Entry<String, String>> pairs = query.queryPairs();
        if (pairs.isEmpty()) return "";
        StringBuilder b = new StringBuilder("?");
        for (Map.Entry<String, String> p : pairs) {
            if (b.length() > 1) b.append('&');
            b.append(encode(p.getKey())).append('=').append(encode(p.getValue()));
        }
        return b.toString();
    }

    private static long seconds(Duration d) {
        long s = d.getSeconds() + (d.getNano() > 0 ? 1 : 0);
        return Math.max(1, s);
    }

    /** Query for long-polling a request resource. */
    static final class WaitQuery extends InputObject {
        WaitQuery(int waitSeconds) {
            setOptional("waitSeconds", waitSeconds);
        }
    }

    /** One logical call: fixed idempotency key, end user, timeouts and retry budget. */
    final class Call {
        final OperationSpec op;
        final RequestOptions o;
        final Duration attemptTimeout;
        /** Deadline for waiting on a pending request: the total timeout if set, else the attempt timeout. */
        final long deadlineNanos;
        /** {@link System#nanoTime()} after which no attempt or retry starts, or null without a total timeout. */
        final Long totalDeadlineNanos;
        final int maxRetries;
        final String idempotencyKey;
        final String endUserId;
        final Duration serverTimeout;

        Call(OperationSpec op, RequestOptions o) {
            this(op, o, true);
        }

        Call(OperationSpec op, RequestOptions o, boolean applyTotalTimeout) {
            this(op, o, applyTotalTimeout, null);
        }

        /** {@code deadlineOverride}: a {@link System#nanoTime()} after which no attempt or retry starts, replacing the total timeout. */
        Call(OperationSpec op, RequestOptions o, boolean applyTotalTimeout, Long deadlineOverride) {
            long started = System.nanoTime();
            this.op = op;
            this.o = o == null ? RequestOptions.NONE : o;
            Duration timeout = this.o.timeout() != null ? this.o.timeout() : options.timeout;
            this.serverTimeout = op.has(OperationSpec.SERVER_TIMEOUT) ? (this.o.serverTimeout() != null ? this.o.serverTimeout() : options.serverTimeout) : null;
            if (serverTimeout != null && serverTimeout.plusSeconds(10).compareTo(timeout) > 0) timeout = serverTimeout.plusSeconds(10);
            this.attemptTimeout = timeout;
            Duration total = applyTotalTimeout ? (this.o.totalTimeout() != null ? this.o.totalTimeout() : options.totalTimeout) : null;
            this.totalDeadlineNanos = deadlineOverride != null ? deadlineOverride : total == null ? null : started + total.toNanos();
            this.deadlineNanos = started + (total != null ? total : timeout).toNanos();
            this.maxRetries = this.o.maxRetries() != null ? this.o.maxRetries() : options.maxRetries;
            if (op.has(OperationSpec.END_USER)) {
                String eu = this.o.endUserId() != null ? this.o.endUserId() : options.endUserId;
                if (eu == null || eu.isEmpty()) {
                    throw new DaapiException(op.operationId() + " runs against an end user's QuickBooks company file, but no end user is set. "
                        + "Pass endUserId(...) to the client builder, use client.forEndUser(\"eu_...\"), or pass RequestOptions.endUser(\"eu_...\").");
                }
                this.endUserId = eu;
            } else {
                this.endUserId = null;
            }
            // One key per logical write, reused by every retry of it.
            this.idempotencyKey = op.has(OperationSpec.WRITE) ? (this.o.idempotencyKey() != null ? this.o.idempotencyKey() : UUID.randomUUID().toString()) : null;
        }

        Transport.Response send(String path, InputObject query, String body, String contentType, String accept, boolean async) {
            Map<String, String> headers = new LinkedHashMap<>(options.defaultHeaders);
            headers.put("Authorization", "Bearer " + options.apiKey);
            headers.put("Accept", accept);
            headers.put("User-Agent", SdkInfo.USER_AGENT);
            if (endUserId != null) headers.put("Daapi-End-User-Id", endUserId);
            if (idempotencyKey != null) headers.put("Idempotency-Key", idempotencyKey);
            if (serverTimeout != null) headers.put("Daapi-Timeout-Seconds", Long.toString(seconds(serverTimeout)));
            if (async) {
                headers.put("Prefer", "respond-async");
                if (op.has(OperationSpec.QUEUE_TTL) && o.queueTtl() != null) headers.put("Daapi-Queue-Ttl-Seconds", Long.toString(seconds(o.queueTtl())));
            }
            if (body != null) headers.put("Content-Type", contentType + ("application/json".equals(contentType) ? "" : "; charset=utf-8"));
            URI uri = URI.create(options.baseUrl + path + queryString(query));
            Map<String, String> fixedHeaders = Collections.unmodifiableMap(headers);
            for (int attempt = 0; ; attempt++) {
                Duration thisAttempt = attemptTimeout;
                if (totalDeadlineNanos != null) {
                    long remaining = totalDeadlineNanos - System.nanoTime();
                    if (remaining <= 0) throw new ApiTimeoutException(op.operationId() + ": the call's total timeout ended before a response arrived", null);
                    if (remaining < thisAttempt.toNanos()) thisAttempt = Duration.ofNanos(remaining);
                }
                Transport.Request req = new Transport.Request(op.method(), uri, fixedHeaders, body, thisAttempt);
                long t0 = System.nanoTime();
                Transport.Response res;
                try {
                    res = options.transport.send(req);
                } catch (IOException e) {
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    boolean timeout = e instanceof HttpTimeoutException;
                    String what = timeout ? "timed out after " + thisAttempt.toMillis() + " ms" : "failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
                    log(op.method() + " " + path + " " + what + " (attempt " + (attempt + 1) + ", " + ms + " ms)");
                    long backoff = Retry.backoffMillis(attempt, JITTER);
                    if (attempt < maxRetries && retryFits(backoff)) {
                        sleep(backoff, "after a network error");
                        continue;
                    }
                    String msg = op.operationId() + " " + what + (attempt > 0 ? " (" + (attempt + 1) + " attempts)" : "");
                    if (timeout) throw new ApiTimeoutException(msg, e);
                    throw new ApiConnectionException(msg, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ApiConnectionException(op.operationId() + " was interrupted", e);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                String rid = res.headers().get("Daapi-Request-Id");
                log(op.method() + " " + path + " -> " + res.status() + (rid == null ? "" : " " + rid) + " (attempt " + (attempt + 1) + ", " + ms + " ms)");
                if (res.status() >= 200 && res.status() < 300) return res;
                ApiException err = toException(res);
                Long retryAfter = Retry.retryAfterMillis(res.headers().get("Retry-After"), Instant.now());
                long delay = retryAfter != null ? retryAfter : Retry.backoffMillis(attempt, JITTER);
                boolean retry = attempt < maxRetries && Retry.shouldRetry(res.status(), res.headers().get("Daapi-Should-Retry"), err.outcome())
                    && (retryAfter == null || retryAfter <= Retry.MAX_RETRY_AFTER_MILLIS) && retryFits(delay);
                if (!retry) throw err;
                sleep(delay, "after HTTP " + res.status());
            }
        }

        /** Whether a retry after {@code delayMillis} can still start before the total timeout ends. */
        private boolean retryFits(long delayMillis) {
            return totalDeadlineNanos == null || System.nanoTime() + delayMillis * 1_000_000L < totalDeadlineNanos;
        }

        private void sleep(long millis, String why) {
            log("retrying " + op.operationId() + " in " + millis + " ms " + why);
            if (millis <= 0) return;
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiConnectionException(op.operationId() + " was interrupted while waiting to retry", e);
            }
        }
    }

    static ApiException toException(Transport.Response res) {
        Object parsed = null;
        try {
            parsed = Json.parse(res.body());
        } catch (Json.JsonException ignored) {
            // Not JSON: falls through to the base ApiException below.
        }
        if (parsed instanceof Map && ((Map<?, ?>) parsed).get("error") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) ((Map<?, ?>) parsed).get("error");
            return ApiException.create(ApiErrorInfo.of(res.status(), error, res.headers(), res.body(), "HTTP " + res.status()));
        }
        String snippet = res.body().length() > 200 ? res.body().substring(0, 200) + "..." : res.body();
        return new ApiException(ApiErrorInfo.of(res.status(), Collections.emptyMap(), res.headers(), res.body(),
            "HTTP " + res.status() + " without a JSON error body" + (snippet.isEmpty() ? "" : ": " + snippet)));
    }
}

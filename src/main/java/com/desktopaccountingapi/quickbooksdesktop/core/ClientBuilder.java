package com.desktopaccountingapi.quickbooksdesktop.core;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings shared by the client builders. Every setting is optional:
 *
 * <table>
 * <caption>Client settings</caption>
 * <tr><th>Setting</th><th>Default</th></tr>
 * <tr><td>{@link #apiKey(String)}</td><td>{@code DAAPI_SECRET_KEY} environment variable</td></tr>
 * <tr><td>{@link #baseUrl(String)}</td><td>{@code DAAPI_BASE_URL}, else {@value ClientOptions#DEFAULT_BASE_URL}</td></tr>
 * <tr><td>{@link #endUserId(String)}</td><td>none; QuickBooks Desktop calls then need a per-call end user</td></tr>
 * <tr><td>{@link #timeout(Duration)}</td><td>100 s per HTTP attempt</td></tr>
 * <tr><td>{@link #totalTimeout(Duration)}</td><td>none; a call's attempts and retries are not capped in total</td></tr>
 * <tr><td>{@link #maxRetries(int)}</td><td>2</td></tr>
 * <tr><td>{@link #serverTimeout(Duration)}</td><td>server default (90 s, health check 60 s)</td></tr>
 * <tr><td>{@link #transport(Transport)} / {@link #httpClient(HttpClient)}</td><td>{@link JavaHttpTransport}</td></tr>
 * <tr><td>{@link #defaultHeader(String, String)} / {@link #defaultHeaders(Map)}</td><td>none</td></tr>
 * <tr><td>{@link #logger(RequestLogger)}</td><td>none</td></tr>
 * </table>
 *
 * @param <B> the concrete builder type
 * @param <C> the client type it builds
 */
public abstract class ClientBuilder<B extends ClientBuilder<B, C>, C> {
    private String apiKey;
    private String baseUrl;
    private String endUserId;
    private Duration timeout;
    private Integer maxRetries;
    private Duration serverTimeout;
    private Transport transport;
    private RequestLogger logger;
    private Duration totalTimeout;
    private final Map<String, String> defaultHeaders = new LinkedHashMap<>();

    /** Creates an empty builder. */
    protected ClientBuilder() {}

    /**
     * The concrete builder, for fluent chaining.
     *
     * @return this builder
     */
    protected abstract B self();

    /**
     * Validates the settings and builds the client.
     *
     * @return the client
     * @throws com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException if the secret key is
     *     missing or malformed, or the base URL is invalid
     */
    public abstract C build();

    /**
     * Resolves the settings (environment variables and defaults) and validates them.
     *
     * @return the options
     */
    protected final ClientOptions resolveOptions() {
        return ClientOptions.resolve(apiKey, baseUrl, endUserId, timeout, maxRetries, serverTimeout, transport, logger)
            .withExtras(totalTimeout, defaultHeaders);
    }

    /**
     * Secret key ({@code sk_live_...} or {@code sk_test_...}). Checked locally before any request.
     *
     * @param apiKey the key
     * @return this builder
     */
    public B apiKey(String apiKey) {
        this.apiKey = apiKey;
        return self();
    }

    /**
     * API base URL. May include a path; the SDK appends {@code /v1/...}. A trailing {@code /v1} is
     * removed, so {@code https://api.desktopaccountingapi.com/v1} works too. Use
     * {@code https://api-staging.desktopaccountingapi.com} for staging.
     *
     * @param baseUrl absolute http(s) URL
     * @return this builder
     */
    public B baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return self();
    }

    /**
     * Default end user ({@code eu_...}) for QuickBooks Desktop operations. See also
     * {@code forEndUser(...)} on the client.
     *
     * @param endUserId the end user
     * @return this builder
     */
    public B endUserId(String endUserId) {
        this.endUserId = endUserId;
        return self();
    }

    /**
     * Client-side timeout per HTTP attempt (default 100 s). Each retry gets a fresh timeout, so a call
     * can take longer; set {@link #totalTimeout(Duration)} to cap the whole call. Without a total
     * timeout, also the time budget of a call that long-polls after a server timeout.
     *
     * @param timeout positive duration
     * @return this builder
     */
    public B timeout(Duration timeout) {
        this.timeout = ClientOptions.positive(timeout, "timeout");
        return self();
    }

    /**
     * Time budget of a whole call: every attempt, retry backoff and the wait for a pending request.
     * An attempt still running when it ends is cut off ({@code ApiTimeoutException}), and no retry
     * starts after it. Default: none.
     *
     * @param totalTimeout positive duration
     * @return this builder
     */
    public B totalTimeout(Duration totalTimeout) {
        this.totalTimeout = ClientOptions.positive(totalTimeout, "totalTimeout");
        return self();
    }

    /**
     * Adds a header sent with every request. Headers the SDK manages ({@code Authorization},
     * {@code Accept}, {@code Content-Type}, {@code User-Agent}, {@code Daapi-End-User-Id},
     * {@code Idempotency-Key}, {@code Daapi-Timeout-Seconds}, {@code Prefer}) are ignored.
     *
     * @param name header name
     * @param value header value
     * @return this builder
     */
    public B defaultHeader(String name, String value) {
        if (name == null || name.isEmpty() || value == null) throw new IllegalArgumentException("header name and value are required");
        this.defaultHeaders.put(name, value);
        return self();
    }

    /**
     * Adds headers sent with every request (see {@link #defaultHeader(String, String)}).
     *
     * @param headers header names to values
     * @return this builder
     */
    public B defaultHeaders(Map<String, String> headers) {
        for (Map.Entry<String, String> h : headers.entrySet()) defaultHeader(h.getKey(), h.getValue());
        return self();
    }

    /**
     * Retries after network errors, 429 and retryable 5xx responses (default 2). 0 disables retries.
     *
     * @param maxRetries 0 or more
     * @return this builder
     */
    public B maxRetries(int maxRetries) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be 0 or more");
        this.maxRetries = maxRetries;
        return self();
    }

    /**
     * Server-side wait for QuickBooks ({@code Daapi-Timeout-Seconds}, whole seconds, 1 to 300), sent
     * on operations that accept it. When it is longer than the client timeout, each attempt waits
     * {@code serverTimeout + 10 s} instead.
     *
     * @param serverTimeout positive duration
     * @return this builder
     */
    public B serverTimeout(Duration serverTimeout) {
        this.serverTimeout = ClientOptions.positive(serverTimeout, "serverTimeout");
        return self();
    }

    /**
     * Custom HTTP transport (proxies, instrumentation, tests).
     *
     * @param transport the transport
     * @return this builder
     */
    public B transport(Transport transport) {
        this.transport = transport;
        return self();
    }

    /**
     * Uses a caller-configured {@link HttpClient} with the default transport.
     *
     * @param httpClient the client
     * @return this builder
     */
    public B httpClient(HttpClient httpClient) {
        this.transport = new JavaHttpTransport(httpClient);
        return self();
    }

    /**
     * Receives one line per HTTP attempt and retry. Never receives keys, headers or bodies.
     *
     * @param logger the logger
     * @return this builder
     */
    public B logger(RequestLogger logger) {
        this.logger = logger;
        return self();
    }
}

package com.desktopaccountingapi.quickbooksdesktop.core;

import com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolved, validated client settings. Created by the client builder; immutable.
 */
public final class ClientOptions {
    /** Production API base URL. */
    public static final String DEFAULT_BASE_URL = "https://api.desktopaccountingapi.com";
    /** Default client-side timeout per HTTP attempt: the server's default 90 s wait plus 10 s. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(100);
    /** Default number of retries. */
    public static final int DEFAULT_MAX_RETRIES = 2;

    final String apiKey;
    final String baseUrl;
    final String endUserId;
    final Duration timeout;
    final int maxRetries;
    final Duration serverTimeout;
    final Transport transport;
    final RequestLogger logger;
    final Duration totalTimeout;
    final Map<String, String> defaultHeaders;

    /** Headers the SDK manages; default headers never supply them. */
    private static final Set<String> MANAGED_HEADERS = Set.of("authorization", "accept", "content-type", "user-agent", "daapi-end-user-id",
        "conductor-end-user-id", "idempotency-key", "daapi-timeout-seconds", "prefer", "daapi-queue-ttl-seconds");

    ClientOptions(String apiKey, String baseUrl, String endUserId, Duration timeout, int maxRetries, Duration serverTimeout, Transport transport, RequestLogger logger) {
        this(apiKey, baseUrl, endUserId, timeout, maxRetries, serverTimeout, transport, logger, null, Collections.emptyMap());
    }

    private ClientOptions(String apiKey, String baseUrl, String endUserId, Duration timeout, int maxRetries, Duration serverTimeout, Transport transport, RequestLogger logger,
                          Duration totalTimeout, Map<String, String> defaultHeaders) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.endUserId = endUserId;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.serverTimeout = serverTimeout;
        this.transport = transport;
        this.logger = logger;
        this.totalTimeout = totalTimeout;
        this.defaultHeaders = defaultHeaders;
    }

    /**
     * Resolves and validates settings; unset values come from the environment or defaults.
     *
     * @param apiKey secret key, or null to read {@code DAAPI_SECRET_KEY}
     * @param baseUrl base URL, or null to read {@code DAAPI_BASE_URL} (default {@value #DEFAULT_BASE_URL})
     * @param endUserId default end user, or null
     * @param timeout client-side timeout, or null for {@link #DEFAULT_TIMEOUT}
     * @param maxRetries retries, or null for {@value #DEFAULT_MAX_RETRIES}
     * @param serverTimeout server-side wait, or null to let the server decide
     * @param transport HTTP transport, or null for a new {@link JavaHttpTransport}
     * @param logger request logger, or null
     * @return the options
     * @throws DaapiException if the key is missing or malformed, or the base URL is invalid
     */
    public static ClientOptions resolve(String apiKey, String baseUrl, String endUserId, Duration timeout, Integer maxRetries, Duration serverTimeout, Transport transport, RequestLogger logger) {
        String key = apiKey != null ? apiKey : System.getenv("DAAPI_SECRET_KEY");
        if (key == null || key.isEmpty()) {
            throw new DaapiException("No secret key. Pass apiKey(...) to the client builder or set the DAAPI_SECRET_KEY environment variable.");
        }
        String problem = ApiKeys.problem(key);
        if (problem != null) throw new DaapiException("Invalid secret key: " + problem + ". Copy the key again from the dashboard (DAAPI_SECRET_KEY).");
        String url = baseUrl != null ? baseUrl : System.getenv("DAAPI_BASE_URL");
        if (url == null || url.isEmpty()) url = DEFAULT_BASE_URL;
        url = url.replaceAll("/+$", "");
        // The SDK appends /v1/...; accept a base URL that already ends in /v1 (Conductor's form).
        if (url.endsWith("/v1")) url = url.substring(0, url.length() - 3);
        try {
            URI u = new URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) && !"http".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getRawQuery() != null || u.getRawFragment() != null) {
                throw new DaapiException("baseUrl must be an absolute http(s) URL without query or fragment");
            }
        } catch (URISyntaxException e) {
            throw new DaapiException("baseUrl is not a valid URL", e);
        }
        if (endUserId != null && endUserId.isEmpty()) endUserId = null;
        return new ClientOptions(key, url, endUserId,
            timeout != null ? timeout : DEFAULT_TIMEOUT,
            maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES,
            serverTimeout,
            transport != null ? transport : new JavaHttpTransport(),
            logger);
    }

    static Duration positive(Duration d, String name) {
        if (d != null && (d.isNegative() || d.isZero())) throw new IllegalArgumentException(name + " must be positive");
        return d;
    }

    /**
     * Copy with another default end user (shares the transport).
     *
     * @param endUserId the end user, or null for none
     * @return the copy
     */
    public ClientOptions withEndUserId(String endUserId) {
        return new ClientOptions(apiKey, baseUrl, endUserId, timeout, maxRetries, serverTimeout, transport, logger, totalTimeout, defaultHeaders);
    }

    /**
     * Copy with a total timeout and default headers (the headers the SDK manages are dropped).
     *
     * @param totalTimeout time budget of a whole call, or null for none
     * @param headers headers sent with every request
     * @return the copy
     */
    public ClientOptions withExtras(Duration totalTimeout, Map<String, String> headers) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (Map.Entry<String, String> h : headers.entrySet()) {
            if (!MANAGED_HEADERS.contains(h.getKey().toLowerCase(Locale.ROOT))) kept.put(h.getKey(), h.getValue());
        }
        return new ClientOptions(apiKey, baseUrl, endUserId, timeout, maxRetries, serverTimeout, transport, logger, positive(totalTimeout, "totalTimeout"),
            Collections.unmodifiableMap(kept));
    }

    /**
     * Base URL without trailing slash.
     *
     * @return the URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Default end user.
     *
     * @return the ID, or null
     */
    public String endUserId() {
        return endUserId;
    }

    /**
     * Client-side timeout per HTTP attempt.
     *
     * @return the timeout
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Time budget of a whole call.
     *
     * @return the duration, or null for none
     */
    public Duration totalTimeout() {
        return totalTimeout;
    }

    /**
     * Headers sent with every request.
     *
     * @return header names to values (unmodifiable)
     */
    public Map<String, String> defaultHeaders() {
        return defaultHeaders;
    }

    /**
     * Retry limit.
     *
     * @return the number of retries
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * Server-side wait sent as {@code Daapi-Timeout-Seconds}.
     *
     * @return the duration, or null
     */
    public Duration serverTimeout() {
        return serverTimeout;
    }

    /**
     * The HTTP transport.
     *
     * @return the transport
     */
    public Transport transport() {
        return transport;
    }

    /**
     * Whether the key is a test-mode key ({@code sk_test_}).
     *
     * @return true for test keys
     */
    public boolean isTestMode() {
        return apiKey.startsWith("sk_test_");
    }

    @Override
    public String toString() {
        // Never include the key.
        return "ClientOptions{baseUrl=" + baseUrl + ", endUserId=" + endUserId + ", timeout=" + timeout + ", totalTimeout=" + totalTimeout + ", maxRetries=" + maxRetries
            + ", serverTimeout=" + serverTimeout + ", defaultHeaders=" + defaultHeaders.keySet() + "}";
    }
}

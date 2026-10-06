package com.desktopaccountingapi.quickbooksdesktop.core;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends one HTTP request. The default is {@link JavaHttpTransport} over
 * {@link java.net.http.HttpClient}; supply your own to add proxies, instrumentation or test doubles.
 *
 * <p>Contract: return the response for any HTTP status (the SDK maps errors and decides on
 * retries); throw {@link IOException} when no response was received (connection failure, reset,
 * timeout). Throw {@link java.net.http.HttpTimeoutException} for timeouts so the SDK can report
 * them as {@link com.desktopaccountingapi.quickbooksdesktop.errors.ApiTimeoutException}. A transport
 * is shared by every client copy created with {@code forEndUser} and must be thread-safe.
 */
@FunctionalInterface
public interface Transport {
    /**
     * Sends the request and reads the whole response.
     *
     * @param request the request
     * @return the response
     * @throws IOException when no response was received
     * @throws InterruptedException when the calling thread was interrupted
     */
    Response send(Request request) throws IOException, InterruptedException;

    /** An outgoing HTTP request. */
    final class Request {
        private final String method;
        private final URI uri;
        private final Map<String, String> headers;
        private final String body;
        private final Duration timeout;

        /**
         * Creates a request.
         *
         * @param method HTTP method
         * @param uri absolute URL
         * @param headers header names to values
         * @param body UTF-8 body, or null
         * @param timeout time to wait for the complete response
         */
        public Request(String method, URI uri, Map<String, String> headers, String body, Duration timeout) {
            this.method = method;
            this.uri = uri;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.body = body;
            this.timeout = timeout;
        }

        /**
         * HTTP method.
         *
         * @return {@code GET}, {@code POST} or {@code DELETE}
         */
        public String method() {
            return method;
        }

        /**
         * Absolute URL including the query string.
         *
         * @return the URL
         */
        public URI uri() {
            return uri;
        }

        /**
         * Request headers, including {@code Authorization}. Never log these.
         *
         * @return header names to values
         */
        public Map<String, String> headers() {
            return headers;
        }

        /**
         * Body text.
         *
         * @return the body, or null
         */
        public String body() {
            return body;
        }

        /**
         * Time to wait for the complete response.
         *
         * @return the timeout
         */
        public Duration timeout() {
            return timeout;
        }
    }

    /** A received HTTP response. */
    final class Response {
        private final int status;
        private final Headers headers;
        private final String body;

        /**
         * Creates a response.
         *
         * @param status HTTP status
         * @param headers response headers
         * @param body body text (empty string if none)
         */
        public Response(int status, Headers headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body == null ? "" : body;
        }

        /**
         * HTTP status.
         *
         * @return the status code
         */
        public int status() {
            return status;
        }

        /**
         * Response headers.
         *
         * @return the headers
         */
        public Headers headers() {
            return headers;
        }

        /**
         * Body text.
         *
         * @return the body (empty string if none)
         */
        public String body() {
            return body;
        }
    }
}

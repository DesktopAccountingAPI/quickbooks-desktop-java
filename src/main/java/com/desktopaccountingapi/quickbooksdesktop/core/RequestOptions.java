package com.desktopaccountingapi.quickbooksdesktop.core;

import java.time.Duration;

/**
 * Per-call overrides of the client settings. Immutable; build with {@link #builder()}.
 *
 * <pre>{@code
 * RequestOptions opts = RequestOptions.builder()
 *     .endUserId("eu_01j9x4m6v4c8k2t7q0r5s3w1zb")
 *     .idempotencyKey("order-8812-invoice")
 *     .timeout(Duration.ofSeconds(30))
 *     .build();
 * client.qbd().invoices().create(input, opts);
 * }</pre>
 */
public final class RequestOptions {
    /** No overrides. */
    public static final RequestOptions NONE = new Builder().build();

    private final String endUserId;
    private final String idempotencyKey;
    private final Duration timeout;
    private final Integer maxRetries;
    private final Duration serverTimeout;
    private final Duration queueTtl;

    private RequestOptions(Builder b) {
        this.endUserId = b.endUserId;
        this.idempotencyKey = b.idempotencyKey;
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        this.serverTimeout = b.serverTimeout;
        this.queueTtl = b.queueTtl;
    }

    /**
     * Starts a builder.
     *
     * @return a builder with no overrides
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Options that only select the end user.
     *
     * @param endUserId the end user ({@code eu_...})
     * @return the options
     */
    public static RequestOptions endUser(String endUserId) {
        return builder().endUserId(endUserId).build();
    }

    /**
     * Options that only set the idempotency key.
     *
     * @param idempotencyKey the key (1 to 255 printable ASCII characters)
     * @return the options
     */
    public static RequestOptions idempotent(String idempotencyKey) {
        return builder().idempotencyKey(idempotencyKey).build();
    }

    /**
     * A builder initialised with these options.
     *
     * @return the builder
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.endUserId = endUserId;
        b.idempotencyKey = idempotencyKey;
        b.timeout = timeout;
        b.maxRetries = maxRetries;
        b.serverTimeout = serverTimeout;
        b.queueTtl = queueTtl;
        return b;
    }

    /**
     * End user override.
     *
     * @return the end user ID, or null to use the client default
     */
    public String endUserId() {
        return endUserId;
    }

    /**
     * Idempotency key override.
     *
     * @return the key, or null to let the SDK generate one per call
     */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Client-side timeout override.
     *
     * @return the timeout, or null to use the client setting
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Retry limit override.
     *
     * @return the limit, or null to use the client setting
     */
    public Integer maxRetries() {
        return maxRetries;
    }

    /**
     * Server-side wait override ({@code Daapi-Timeout-Seconds}).
     *
     * @return the duration, or null to use the client setting
     */
    public Duration serverTimeout() {
        return serverTimeout;
    }

    /**
     * Async queue lifetime ({@code Daapi-Queue-Ttl-Seconds}); used by {@code enqueue()} calls only.
     *
     * @return the duration, or null for the server default
     */
    public Duration queueTtl() {
        return queueTtl;
    }

    /** Builder for {@link RequestOptions}. */
    public static final class Builder {
        private String endUserId;
        private String idempotencyKey;
        private Duration timeout;
        private Integer maxRetries;
        private Duration serverTimeout;
        private Duration queueTtl;

        private Builder() {}

        /**
         * Runs this call against another end user's company file. Ignored by platform operations.
         *
         * @param endUserId the end user ({@code eu_...})
         * @return this builder
         */
        public Builder endUserId(String endUserId) {
            this.endUserId = endUserId;
            return this;
        }

        /**
         * Idempotency key for a write. Without one the SDK generates a UUID per call and reuses it on
         * every retry of that call. Set your own (for example derived from an order ID) to make
         * repeated calls across process restarts safe.
         *
         * @param idempotencyKey 1 to 255 printable ASCII characters
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Client-side timeout for each HTTP attempt, and the time budget of the whole call when the SDK
         * long-polls a request that timed out on the server.
         *
         * @param timeout positive duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = ClientOptions.positive(timeout, "timeout");
            return this;
        }

        /**
         * Retries after network errors, 429 and retryable 5xx responses. 0 disables retries.
         *
         * @param maxRetries 0 or more
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be 0 or more");
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * How long the API waits for QuickBooks before answering {@code 504}
         * ({@code Daapi-Timeout-Seconds}, whole seconds, 1 to 300). Sent only on operations that
         * accept it.
         *
         * @param serverTimeout positive duration
         * @return this builder
         */
        public Builder serverTimeout(Duration serverTimeout) {
            this.serverTimeout = ClientOptions.positive(serverTimeout, "serverTimeout");
            return this;
        }

        /**
         * How long an async request may wait for an offline connector before it expires
         * ({@code Daapi-Queue-Ttl-Seconds}). Used by {@code enqueue()} calls only.
         *
         * @param queueTtl positive duration
         * @return this builder
         */
        public Builder queueTtl(Duration queueTtl) {
            this.queueTtl = ClientOptions.positive(queueTtl, "queueTtl");
            return this;
        }

        /**
         * Builds the options.
         *
         * @return immutable options
         */
        public RequestOptions build() {
            return new RequestOptions(this);
        }
    }
}

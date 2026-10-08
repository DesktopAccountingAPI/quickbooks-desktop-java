package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * Base class of every exception the SDK throws.
 *
 * <p>Thrown directly for client-side problems detected before a request is sent: a missing or
 * malformed secret key, a QuickBooks Desktop operation without an end user, a required input field
 * that was not set, or a response body the SDK cannot parse. API error responses throw
 * {@link ApiException} and its subclasses.
 */
public class DaapiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     */
    public DaapiException(String message) {
        super(message);
    }

    /**
     * Creates the exception with an underlying cause.
     *
     * @param message what went wrong
     * @param cause the underlying exception
     */
    public DaapiException(String message, Throwable cause) {
        super(message, cause);
    }

    private String idempotencyKey;

    /**
     * Creates the exception for a write sent with an idempotency key.
     *
     * @param message what went wrong
     * @param cause the underlying exception, or null
     * @param idempotencyKey the write's {@code Idempotency-Key}, or null
     */
    protected DaapiException(String message, Throwable cause, String idempotencyKey) {
        super(message, cause);
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * The {@code Idempotency-Key} the SDK sent for the write that raised this exception (generated
     * once per call unless you set {@code RequestOptions.idempotencyKey}), else null. Resend a write
     * whose outcome is {@code pending} or {@code unknown}, or that failed without a response, only
     * with this key: the API then returns the original request instead of writing twice.
     *
     * @return the key, or null
     */
    public final String idempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Records the write's idempotency key. Used by the SDK; the first key recorded is kept.
     *
     * @param key the key, or null
     * @return this exception
     */
    public final DaapiException attachIdempotencyKey(String key) {
        if (idempotencyKey == null) idempotencyKey = key;
        return this;
    }
}

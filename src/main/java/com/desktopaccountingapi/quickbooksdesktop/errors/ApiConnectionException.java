package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * No HTTP response was received: the connection failed, was reset or dropped, or the thread was
 * interrupted. Thrown after the SDK's retries are used up. A write may still have reached the API;
 * retrying with the same idempotency key is safe.
 */
public class ApiConnectionException extends DaapiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what failed
     * @param cause the underlying I/O exception
     */
    public ApiConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

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
}

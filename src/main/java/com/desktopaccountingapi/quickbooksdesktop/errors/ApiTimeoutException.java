package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * The client-side HTTP timeout ({@code timeout} option) elapsed before a response arrived, on every
 * attempt. Raise the timeout, or use {@code serverTimeout} / async mode for long QuickBooks
 * operations.
 */
public class ApiTimeoutException extends ApiConnectionException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what timed out
     * @param cause the underlying timeout exception
     */
    public ApiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

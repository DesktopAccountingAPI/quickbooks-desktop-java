package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * An internal error in Desktop Accounting API. Check {@link #outcome()}: when it is {@code pending} or {@code unknown}, look up the request with {@code client.requests().retrieve(...)} before retrying.
 *
 * <p>Thrown for error type {@code INTERNAL_ERROR}.
 */
public class InternalException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public InternalException(ApiErrorInfo info) {
        super(info);
    }
}

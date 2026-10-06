package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * The project's billing state blocks the request (HTTP 402).
 *
 * <p>Thrown for error type {@code BILLING_ERROR}.
 */
public class BillingException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public BillingException(ApiErrorInfo info) {
        super(info);
    }
}

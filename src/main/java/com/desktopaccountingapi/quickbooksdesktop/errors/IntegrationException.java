package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * QuickBooks Desktop rejected the request (for example an unknown list ID or a duplicate name). {@link #integrationCode()} carries the qbXML status code.
 *
 * <p>Thrown for error type {@code INTEGRATION_ERROR}.
 */
public class IntegrationException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public IntegrationException(ApiErrorInfo info) {
        super(info);
    }
}

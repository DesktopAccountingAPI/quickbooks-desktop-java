package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * A problem in the end user's environment: QuickBooks Desktop is closed, a dialog is open, the Web Connector is offline, or the request timed out in the queue. Usually needs the end user to act; see {@link #fixes()} and {@link #userFacingMessage()}.
 *
 * <p>Thrown for error type {@code INTEGRATION_CONNECTION_ERROR}.
 */
public class IntegrationConnectionException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public IntegrationConnectionException(ApiErrorInfo info) {
        super(info);
    }
}

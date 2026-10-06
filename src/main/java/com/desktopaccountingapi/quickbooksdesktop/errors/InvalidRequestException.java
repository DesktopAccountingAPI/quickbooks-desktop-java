package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * The request is invalid: a parameter failed validation, a referenced object does not exist on the API side, or a cursor is invalid. Fix the request before retrying.
 *
 * <p>Thrown for error type {@code INVALID_REQUEST_ERROR}.
 */
public class InvalidRequestException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public InvalidRequestException(ApiErrorInfo info) {
        super(info);
    }
}

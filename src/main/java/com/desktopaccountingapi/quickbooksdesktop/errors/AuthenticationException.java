package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * The secret key is missing, malformed, revoked or for another environment (HTTP 401).
 *
 * <p>Thrown for error type {@code AUTHENTICATION_ERROR}.
 */
public class AuthenticationException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public AuthenticationException(ApiErrorInfo info) {
        super(info);
    }
}

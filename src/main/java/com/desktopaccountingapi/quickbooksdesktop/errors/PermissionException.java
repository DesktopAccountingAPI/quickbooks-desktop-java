package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * The key is valid but not allowed to perform this operation or access this end user (HTTP 403).
 *
 * <p>Thrown for error type {@code PERMISSION_ERROR}.
 */
public class PermissionException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public PermissionException(ApiErrorInfo info) {
        super(info);
    }
}

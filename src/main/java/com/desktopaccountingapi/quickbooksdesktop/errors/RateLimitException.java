package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * Too many requests for the project or the connection (HTTP 429). The SDK retries these automatically, honoring {@code Retry-After}.
 *
 * <p>Thrown for error type {@code RATE_LIMIT_ERROR}.
 */
public class RateLimitException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public RateLimitException(ApiErrorInfo info) {
        super(info);
    }
}

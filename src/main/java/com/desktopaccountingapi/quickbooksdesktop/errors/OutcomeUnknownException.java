package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * A write was sent to QuickBooks Desktop but no result came back, so it may or may not have been applied. The SDK never retries it. Look the record up (for example by {@code externalId} or {@code refNumber}) before trying again.
 *
 * <p>Thrown for error type {@code OUTCOME_UNKNOWN_ERROR}.
 */
public class OutcomeUnknownException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public OutcomeUnknownException(ApiErrorInfo info) {
        super(info);
    }
}

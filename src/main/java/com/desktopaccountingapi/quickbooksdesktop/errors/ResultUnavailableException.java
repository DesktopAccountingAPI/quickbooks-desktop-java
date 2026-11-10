package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * The result is no longer available. For a write that was applied ({@link #outcome()}
 * {@code applied}), never send it again: read the object instead, by {@link #recoveredObjectId()}
 * when it is set (the platform recovered the write after a lost acknowledgement), or by your
 * {@code externalId} or {@code refNumber}. For a read ({@link #outcome()} {@code not_applicable}),
 * it is safe to send the read again.
 *
 * <p>Thrown for a recovered write ({@code result} null) and for {@code 500 INTERNAL_ERROR} with
 * {@code details.reason} {@code result_unavailable}. The SDK never retries it.
 */
// It extends InternalException because the API classifies it as INTERNAL_ERROR (exception classes
// follow error.type): catch it first to tell them apart.
public class ResultUnavailableException extends InternalException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public ResultUnavailableException(ApiErrorInfo info) {
        super(info);
    }

    /**
     * Whether an error with these fields is a result-unavailable error ({@code INTERNAL_ERROR} with
     * {@code details.reason} {@code result_unavailable}).
     *
     * @param info the error fields
     * @return true when it is
     */
    static boolean matches(ApiErrorInfo info) {
        return "INTERNAL_ERROR".equals(info.type) && info.details != null && "result_unavailable".equals(info.details.get("reason"));
    }

    /**
     * QuickBooks ID of the object the write created or changed ({@code details.recoveredObjectId}).
     *
     * @return the ID, or null when the API did not name one
     */
    public String recoveredObjectId() {
        Object id = details().get("recoveredObjectId");
        return id instanceof String && !((String) id).isEmpty() ? (String) id : null;
    }
}

package com.desktopaccountingapi.quickbooksdesktop.errors;

import com.desktopaccountingapi.quickbooksdesktop.core.Headers;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The API answered with an error (any non-2xx response, or a request resource that ended in
 * {@code failed}, {@code canceled} or {@code outcome_unknown}).
 *
 * <p>The SDK throws one subclass per error {@link #type()}: {@link InvalidRequestException},
 * {@link AuthenticationException}, {@link PermissionException}, {@link BillingException},
 * {@link RateLimitException}, {@link IntegrationConnectionException}, {@link IntegrationException},
 * {@link OutcomeUnknownException} and {@link InternalException}. An unknown type, or an error body
 * that is not JSON, throws this base class. Compare {@link #code()} with the constants in
 * {@code models.ErrorCode}.
 *
 * <p>The API's {@code cause} field is exposed as {@link #errorCause()} because
 * {@link Throwable#getCause()} already means the chained Java exception.
 */
public class ApiException extends DaapiException {
    private static final long serialVersionUID = 1L;
    private final transient ApiErrorInfo info;

    /** One suggested fix: who should act and what they should do. */
    public static final class Fix {
        private final String actor;
        private final String action;

        /**
         * Creates a fix.
         *
         * @param actor {@code developer}, {@code end_user} or {@code support} (open set)
         * @param action what to do
         */
        public Fix(String actor, String action) {
            this.actor = actor;
            this.action = action;
        }

        /**
         * Who should act.
         *
         * @return {@code developer}, {@code end_user}, {@code support} or a newer value
         */
        public String actor() {
            return actor;
        }

        /**
         * What to do.
         *
         * @return the instruction
         */
        public String action() {
            return action;
        }

        @Override
        public String toString() {
            return actor + ": " + action;
        }
    }

    /**
     * Creates the exception from parsed error fields.
     *
     * @param info the error fields
     */
    public ApiException(ApiErrorInfo info) {
        super(info.message);
        this.info = info;
    }

    /**
     * Builds the exception class that matches the error's {@code type} (and
     * {@link CursorExpiredException} for {@code CURSOR_EXPIRED}).
     *
     * @param info the error fields
     * @return the typed exception
     */
    public static ApiException create(ApiErrorInfo info) {
        String type = info.type == null ? "" : info.type;
        switch (type) {
            case "INVALID_REQUEST_ERROR":
                if ("CURSOR_EXPIRED".equals(info.code)) return new CursorExpiredException(info);
                return new InvalidRequestException(info);
            case "AUTHENTICATION_ERROR":
                return new AuthenticationException(info);
            case "PERMISSION_ERROR":
                return new PermissionException(info);
            case "BILLING_ERROR":
                return new BillingException(info);
            case "RATE_LIMIT_ERROR":
                return new RateLimitException(info);
            case "INTEGRATION_CONNECTION_ERROR":
                return new IntegrationConnectionException(info);
            case "INTEGRATION_ERROR":
                return new IntegrationException(info);
            case "OUTCOME_UNKNOWN_ERROR":
                return new OutcomeUnknownException(info);
            case "INTERNAL_ERROR":
                return new InternalException(info);
            default:
                return new ApiException(info);
        }
    }

    /**
     * The error fields this exception was built from.
     *
     * @return the fields
     */
    protected final ApiErrorInfo info() {
        return info;
    }

    /**
     * HTTP status of the response. For errors read from a request resource this is the error's
     * {@code httpStatusCode}, which can be null.
     *
     * @return the status, or null
     */
    public Integer status() {
        return info.status;
    }

    /**
     * Error type, for example {@code INTEGRATION_CONNECTION_ERROR}. Null if the body was not JSON.
     *
     * @return the type (see {@code models.ErrorType})
     */
    public String type() {
        return info.type;
    }

    /**
     * Stable catalog code, for example {@code QBD_MODAL_DIALOG_OPEN}. Null if the body was not JSON.
     *
     * @return the code (see {@code models.ErrorCode})
     */
    public String code() {
        return info.code;
    }

    /**
     * Developer-facing description; same as {@link #getMessage()}.
     *
     * @return the message
     */
    public String message() {
        return info.message;
    }

    /**
     * Message that is safe to show to the end user.
     *
     * @return the message, or null if the body was not JSON
     */
    public String userFacingMessage() {
        return info.userFacingMessage;
    }

    /**
     * {@code httpStatusCode} from the error body.
     *
     * @return the code, or null
     */
    public Integer httpStatusCode() {
        return info.httpStatusCode;
    }

    /**
     * Native QuickBooks or Web Connector code, for example {@code 3200} or {@code 0x80040414}.
     *
     * @return the code, or null
     */
    public String integrationCode() {
        return info.integrationCode;
    }

    /**
     * ID of the API call: the body's {@code requestId}, else the {@code Daapi-Request-Id} header.
     *
     * @return the request ID, or null
     */
    public String requestId() {
        return info.requestId;
    }

    /**
     * Same as {@link #requestId()}, for code and tools that expect bean-style getters. Include it
     * when you contact support. {@link #getMessage()} is the API message only; {@link #toString()}
     * adds the status, code and request ID.
     *
     * @return the request ID, or null
     */
    public String getRequestId() {
        return info.requestId;
    }

    /**
     * Why this error happens (the API's {@code cause} field).
     *
     * @return the explanation, or null
     */
    public String errorCause() {
        return info.cause;
    }

    /**
     * Suggested fixes.
     *
     * @return the fixes (empty if none)
     */
    public List<Fix> fixes() {
        return info.fixes;
    }

    /**
     * Documentation page for this error code.
     *
     * @return the URL, or null
     */
    public String docsUrl() {
        return info.docsUrl;
    }

    /**
     * Whether repeating the identical request can succeed without changes.
     *
     * @return the flag, or null if the body was not JSON
     */
    public Boolean retryable() {
        return info.retryable;
    }

    /**
     * Whether the operation took effect: {@code applied}, {@code not_applied}, {@code pending},
     * {@code unknown} or {@code not_applicable}.
     *
     * @return the outcome, or null
     */
    public String outcome() {
        return info.outcome;
    }

    /**
     * Request field path the error refers to, for example {@code lines[2].amount}.
     *
     * @return the path, or null
     */
    public String param() {
        return info.param;
    }

    /**
     * Code-specific details (for example {@code pagesServed} for {@code CURSOR_EXPIRED}).
     *
     * @return unmodifiable map (empty if none)
     */
    public Map<String, Object> details() {
        return info.details == null ? Collections.emptyMap() : info.details;
    }

    /**
     * Response headers.
     *
     * @return the headers (empty for errors read from a request resource)
     */
    public Headers headers() {
        return info.headers;
    }

    /**
     * The raw response body.
     *
     * @return the body, or null for errors read from a request resource
     */
    public String rawBody() {
        return info.rawBody;
    }

    /**
     * The exception with its status, error code and request ID, for loggers and uncaught-exception
     * output: {@code ...IntegrationException: 404 QBD_OBJECT_NOT_FOUND The QuickBooks object does not
     * exist. (req_...)}. {@link #getMessage()} stays the API message.
     *
     * @return the description
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(getClass().getName()).append(": ");
        if (info.status != null) b.append(info.status).append(' ');
        if (info.code != null) b.append(info.code).append(' ');
        b.append(info.message);
        if (info.requestId != null) b.append(" (").append(info.requestId).append(')');
        return b.toString();
    }
}

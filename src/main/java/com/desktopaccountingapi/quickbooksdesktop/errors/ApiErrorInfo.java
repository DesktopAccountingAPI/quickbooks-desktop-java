package com.desktopaccountingapi.quickbooksdesktop.errors;

import com.desktopaccountingapi.quickbooksdesktop.core.Headers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The fields of one API error, parsed from the {@code error} object of an error response (or of a
 * request resource). Shared by every {@link ApiException} subclass.
 */
public final class ApiErrorInfo {
    final Integer status;
    final String type;
    final String code;
    final String message;
    final String userFacingMessage;
    final Integer httpStatusCode;
    final String integrationCode;
    final String requestId;
    final String cause;
    final List<ApiException.Fix> fixes;
    final String docsUrl;
    final Boolean retryable;
    final String outcome;
    final String param;
    final Map<String, Object> details;
    final Headers headers;
    final String rawBody;

    private ApiErrorInfo(Integer status, Map<String, Object> e, Headers headers, String rawBody, String fallbackMessage) {
        this.status = status;
        this.type = str(e.get("type"));
        this.code = str(e.get("code"));
        String m = str(e.get("message"));
        this.message = m != null ? m : fallbackMessage;
        this.userFacingMessage = str(e.get("userFacingMessage"));
        this.httpStatusCode = integer(e.get("httpStatusCode"));
        this.integrationCode = str(e.get("integrationCode"));
        String rid = str(e.get("requestId"));
        this.requestId = rid != null ? rid : headers.get("Daapi-Request-Id");
        this.cause = str(e.get("cause"));
        List<ApiException.Fix> f = new ArrayList<>();
        Object fx = e.get("fixes");
        if (fx instanceof List) {
            for (Object o : (List<?>) fx) {
                if (o instanceof Map) f.add(new ApiException.Fix(str(((Map<?, ?>) o).get("actor")), str(((Map<?, ?>) o).get("action"))));
            }
        }
        this.fixes = Collections.unmodifiableList(f);
        this.docsUrl = str(e.get("docsUrl"));
        this.retryable = e.get("retryable") instanceof Boolean ? (Boolean) e.get("retryable") : null;
        this.outcome = str(e.get("outcome"));
        this.param = str(e.get("param"));
        Object d = e.get("details");
        Map<String, Object> details = Collections.emptyMap();
        if (d instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dm = (Map<String, Object>) d;
            details = Collections.unmodifiableMap(dm);
        }
        this.details = details;
        this.headers = headers;
        this.rawBody = rawBody;
    }

    /**
     * Parses an error object.
     *
     * @param status HTTP status of the response, or null for an error read from a request resource
     *     whose {@code httpStatusCode} is null
     * @param error the {@code error} object (may be empty for non-JSON responses)
     * @param headers response headers
     * @param rawBody the raw response body, if any
     * @param fallbackMessage message to use when the body carries none
     * @return the parsed fields
     */
    public static ApiErrorInfo of(Integer status, Map<String, Object> error, Headers headers, String rawBody, String fallbackMessage) {
        return new ApiErrorInfo(status, error, headers == null ? Headers.empty() : headers, rawBody, fallbackMessage);
    }

    private static String str(Object v) {
        return v instanceof String ? (String) v : null;
    }

    private static Integer integer(Object v) {
        if (v instanceof BigDecimal) {
            try {
                return ((BigDecimal) v).intValueExact();
            } catch (ArithmeticException e) {
                return null;
            }
        }
        return v instanceof Integer ? (Integer) v : null;
    }
}

package com.desktopaccountingapi.quickbooksdesktop.core;

/**
 * A parsed result together with the HTTP response it came from. Returned by the
 * {@code ...WithResponse(...)} methods.
 *
 * <pre>{@code
 * ApiResponse<Customer> r = client.qbd().customers().retrieveWithResponse("80000001-1700000000");
 * System.out.println(r.statusCode() + " " + r.requestId() + " warnings=" + r.headers().get("Daapi-Warnings"));
 * Customer customer = r.data();
 * }</pre>
 *
 * @param <T> the result type
 */
public final class ApiResponse<T> {
    private final T data;
    private final int statusCode;
    private final Headers headers;

    /**
     * Creates the response.
     *
     * @param data parsed result
     * @param statusCode HTTP status
     * @param headers response headers
     */
    public ApiResponse(T data, int statusCode, Headers headers) {
        this.data = data;
        this.statusCode = statusCode;
        this.headers = headers;
    }

    /**
     * The parsed result.
     *
     * @return the result
     */
    public T data() {
        return data;
    }

    /**
     * HTTP status of the final response.
     *
     * @return the status, for example 200 or 201
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Response headers (case-insensitive), including {@code Daapi-Warnings} and
     * {@code Daapi-Idempotent-Replayed}.
     *
     * @return the headers
     */
    public Headers headers() {
        return headers;
    }

    /**
     * The {@code Daapi-Request-Id} header.
     *
     * @return the request ID, or null
     */
    public String requestId() {
        return headers.get("Daapi-Request-Id");
    }
}

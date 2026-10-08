package com.desktopaccountingapi.quickbooksdesktop.errors;

import com.desktopaccountingapi.quickbooksdesktop.models.Request;

/**
 * The call stopped waiting while the request was still queued or running in QuickBooks Desktop:
 * its time budget ran out, or a poll failed (429, 5xx, 404, network). A failed poll says nothing
 * about the write, so the SDK throws this instead of the poll's own exception. The request was not
 * canceled and may still complete: look it up later with
 * {@code client.requests().retrieve(requestId())}, or wait for the {@code request.succeeded} /
 * {@code request.failed} webhook. Never resubmit a write in this state with a new key; that could
 * create a duplicate. Resending with {@link #idempotencyKey()} returns the original request.
 */
public class RequestPendingException extends DaapiException {
    private static final long serialVersionUID = 1L;
    private final String requestId;
    private final transient Request request;
    private final ApiException timeoutError;
    private final DaapiException pollError;

    /**
     * Creates the exception.
     *
     * @param requestId ID of the pending request ({@code req_...})
     * @param request last snapshot of the request, or null if none was read
     */
    public RequestPendingException(String requestId, Request request) {
        this(requestId, request, null, null, null);
    }

    /**
     * Creates the exception with the error that started the wait and the poll failure that ended it.
     *
     * @param requestId ID of the pending request ({@code req_...})
     * @param request last snapshot of the request, or null if none was read
     * @param timeoutError the {@code 504 QBD_REQUEST_TIMEOUT} that started the wait (also the cause), or null
     * @param pollError the failed poll that ended the wait, or null
     * @param idempotencyKey the write's {@code Idempotency-Key}, or null
     */
    public RequestPendingException(String requestId, Request request, ApiException timeoutError, DaapiException pollError, String idempotencyKey) {
        super("Request " + requestId + " is still " + (request != null && request.status() != null ? request.status() : "pending")
            + (pollError == null ? " after the timeout" : ": checking it failed (" + pollError.getMessage() + ")")
            + ". It was not canceled; retrieve it later with client.requests().retrieve(\"" + requestId + "\")"
            + (idempotencyKey == null ? "." : " or resend it only with Idempotency-Key " + idempotencyKey + "."),
            timeoutError != null ? timeoutError : pollError, idempotencyKey);
        this.requestId = requestId;
        this.request = request;
        this.timeoutError = timeoutError;
        this.pollError = pollError;
    }

    /**
     * The {@code 504 QBD_REQUEST_TIMEOUT} exception (with {@code details.diagnosis}) that started the wait.
     *
     * @return the exception, or null
     */
    public ApiException timeoutError() {
        return timeoutError;
    }

    /**
     * The failed poll that ended the wait. It says nothing about the write.
     *
     * @return the exception, or null when the time budget ended
     */
    public DaapiException pollError() {
        return pollError;
    }

    /**
     * ID of the pending request.
     *
     * @return the {@code req_...} ID
     */
    public String requestId() {
        return requestId;
    }

    /**
     * The last request snapshot the SDK read.
     *
     * @return the snapshot, or null
     */
    public Request request() {
        return request;
    }
}

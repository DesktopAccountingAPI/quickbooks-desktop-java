package com.desktopaccountingapi.quickbooksdesktop.errors;

import com.desktopaccountingapi.quickbooksdesktop.models.Request;

/**
 * The call's time budget ran out while the request was still queued or running in QuickBooks
 * Desktop. The request was not canceled and may still complete: look it up later with
 * {@code client.requests().retrieve(requestId())}, or wait for the {@code request.succeeded} /
 * {@code request.failed} webhook. Never resubmit a write in this state; that could create a
 * duplicate.
 */
public class RequestPendingException extends DaapiException {
    private static final long serialVersionUID = 1L;
    private final String requestId;
    private final transient Request request;

    /**
     * Creates the exception.
     *
     * @param requestId ID of the pending request ({@code req_...})
     * @param request last snapshot of the request, or null if none was read
     */
    public RequestPendingException(String requestId, Request request) {
        super("Request " + requestId + " is still " + (request != null && request.status() != null ? request.status() : "pending")
            + " after the timeout. It was not canceled; retrieve it later with client.requests().retrieve(\"" + requestId + "\").");
        this.requestId = requestId;
        this.request = request;
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

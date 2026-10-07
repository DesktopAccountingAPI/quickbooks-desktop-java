package com.desktopaccountingapi.quickbooksdesktop.core;

import com.desktopaccountingapi.quickbooksdesktop.errors.RequestPendingException;
import com.desktopaccountingapi.quickbooksdesktop.models.Request;
import java.time.Duration;
import java.util.function.Function;

/**
 * A request queued in async mode ({@code Prefer: respond-async}), returned by the {@code enqueue()}
 * methods. The call returns as soon as the API accepted the request; the result arrives later.
 *
 * <pre>{@code
 * RequestHandle<Invoice> handle = client.qbd().invoices().enqueue().create(input);
 * System.out.println(handle.id() + " " + handle.request().status());   // req_... queued
 * Invoice invoice = handle.await(Duration.ofMinutes(5));               // long-polls
 * }</pre>
 *
 * <p>Java has no method named {@code wait()} available ({@link Object#wait()} is final), so the
 * long-poll is {@link #await()}.
 *
 * @param <T> the operation's result type
 */
public final class RequestHandle<T> {
    private final ClientCore core;
    private final OperationSpec op;
    private final String id;
    private final Request request;
    private final Function<Object, T> parse;
    private final RequestOptions options;
    private final boolean completed;
    private final T completedResult;

    RequestHandle(ClientCore core, OperationSpec op, String id, Request request, Function<Object, T> parse, RequestOptions options) {
        this.core = core;
        this.op = op;
        this.id = id;
        this.request = request;
        this.parse = parse;
        this.options = options;
        this.completed = false;
        this.completedResult = null;
    }

    private RequestHandle(ClientCore core, OperationSpec op, String id, T result, RequestOptions options) {
        this.core = core;
        this.op = op;
        this.id = id;
        this.request = null;
        this.parse = null;
        this.options = options;
        this.completed = true;
        this.completedResult = result;
    }

    static <T> RequestHandle<T> completed(ClientCore core, OperationSpec op, String id, T result, RequestOptions options) {
        return new RequestHandle<>(core, op, id, result, options);
    }

    /**
     * Request ID ({@code req_...}).
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * The request snapshot returned with {@code 202 Accepted}.
     *
     * @return the snapshot, or null if the API returned the final result immediately
     */
    public Request request() {
        return request;
    }

    /**
     * Reads the request's current state once, without waiting.
     *
     * @return the request resource
     */
    public Request status() {
        return ClientCore.parseRequest(core.readRequestAnyStatus(id, options));
    }

    /**
     * Waits for the result using the total timeout as the budget, else the client's timeout.
     *
     * @return the result
     * @throws RequestPendingException if the request has not finished in time (it keeps running)
     * @throws com.desktopaccountingapi.quickbooksdesktop.errors.ApiException if it failed, was
     *     canceled, or its outcome is unknown
     */
    public T await() {
        if (options.totalTimeout() != null) return await(options.totalTimeout());
        if (core.options().totalTimeout() != null) return await(core.options().totalTimeout());
        return await(options.timeout() != null ? options.timeout() : core.options().timeout());
    }

    /**
     * Long-polls ({@code GET /v1/requests/{id}?waitSeconds=...}) until the request finishes or the
     * timeout passes.
     *
     * @param timeout total time to wait
     * @return the result
     * @throws RequestPendingException if the request has not finished in time (it keeps running)
     * @throws com.desktopaccountingapi.quickbooksdesktop.errors.ApiException if it failed, was
     *     canceled, or its outcome is unknown
     */
    public T await(Duration timeout) {
        if (completed) return completedResult;
        ClientCore.Polled p = core.poll(id, System.nanoTime() + timeout.toNanos(), options);
        return ClientCore.parseResult(op, p.result, parse);
    }

    /**
     * Reads the request once and returns its result if it has finished.
     *
     * @return the result
     * @throws RequestPendingException if the request has not finished yet
     * @throws com.desktopaccountingapi.quickbooksdesktop.errors.ApiException if it failed, was
     *     canceled, or its outcome is unknown
     */
    public T result() {
        if (completed) return completedResult;
        ClientCore.Polled p = core.readRequest(id, null, options);
        if (!p.done) throw new RequestPendingException(id, ClientCore.parseRequest(p.raw));
        return ClientCore.parseResult(op, p.result, parse);
    }

    @Override
    public String toString() {
        return "RequestHandle{" + id + "}";
    }
}

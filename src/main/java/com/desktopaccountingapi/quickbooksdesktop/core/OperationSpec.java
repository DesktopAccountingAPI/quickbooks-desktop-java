package com.desktopaccountingapi.quickbooksdesktop.core;

/**
 * Static description of one API operation, generated from the contract. Internal to the SDK; not a
 * stable API.
 */
public final class OperationSpec {
    /** The operation accepts {@code Idempotency-Key} (a write). */
    public static final int WRITE = 1;
    /** The operation requires {@code Daapi-End-User-Id}. */
    public static final int END_USER = 1 << 1;
    /** The operation accepts {@code Daapi-Timeout-Seconds}. */
    public static final int SERVER_TIMEOUT = 1 << 2;
    /** The operation accepts {@code Prefer: respond-async}. */
    public static final int ASYNC = 1 << 3;
    /** The operation accepts {@code Daapi-Queue-Ttl-Seconds}. */
    public static final int QUEUE_TTL = 1 << 4;

    private final String operationId;
    private final String method;
    private final int flags;

    private OperationSpec(String operationId, String method, int flags) {
        this.operationId = operationId;
        this.method = method;
        this.flags = flags;
    }

    /**
     * Describes an operation.
     *
     * @param operationId contract operation ID, for example {@code qbd.invoices.create}
     * @param method HTTP method
     * @param flags combination of the flag constants
     * @return the description
     */
    public static OperationSpec of(String operationId, String method, int flags) {
        return new OperationSpec(operationId, method, flags);
    }

    /**
     * Contract operation ID.
     *
     * @return the ID
     */
    public String operationId() {
        return operationId;
    }

    /**
     * HTTP method.
     *
     * @return the method
     */
    public String method() {
        return method;
    }

    /**
     * Whether a flag is set.
     *
     * @param flag one of the flag constants
     * @return true if set
     */
    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}

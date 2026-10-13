package com.desktopaccountingapi.quickbooksdesktop.errors;

import com.desktopaccountingapi.quickbooksdesktop.core.OpenEnumValue;
import java.util.Objects;

/**
 * Why a cursor expired ({@link CursorExpiredException#reason()}), an open enum: compare with the
 * constants or switch on {@link #known()}; a value added after this SDK version is kept in
 * {@link #value()}.
 */
public final class CursorExpiredReason implements OpenEnumValue {
    /** The QuickBooks iterator was idle longer than the cursor lifetime. */
    public static final CursorExpiredReason IDLE_TIMEOUT = new CursorExpiredReason("idle_timeout", Known.IDLE_TIMEOUT);
    /** The Web Connector session that held the iterator ended. */
    public static final CursorExpiredReason SESSION_ENDED = new CursorExpiredReason("session_ended", Known.SESSION_ENDED);
    /** QuickBooks restarted and discarded the iterator. */
    public static final CursorExpiredReason QUICKBOOKS_RESTARTED = new CursorExpiredReason("quickbooks_restarted", Known.QUICKBOOKS_RESTARTED);
    /** A newer cursor list for the same end user evicted it. */
    public static final CursorExpiredReason EVICTED = new CursorExpiredReason("evicted", Known.EVICTED);

    /** The known values, for an exhaustive {@code switch}; {@code UNRECOGNIZED} for any other value. */
    public enum Known {
        /** {@code idle_timeout} */
        IDLE_TIMEOUT,
        /** {@code session_ended} */
        SESSION_ENDED,
        /** {@code quickbooks_restarted} */
        QUICKBOOKS_RESTARTED,
        /** {@code evicted} */
        EVICTED,
        /** A value added after this SDK version. */
        UNRECOGNIZED
    }

    private final String value;
    private final Known known;

    private CursorExpiredReason(String value, Known known) {
        this.value = value;
        this.known = known;
    }

    /**
     * The reason for a wire value, known or not.
     *
     * @param value the wire value
     * @return the constant for a known value, else a new instance
     */
    public static CursorExpiredReason of(String value) {
        Objects.requireNonNull(value, "value");
        for (CursorExpiredReason r : new CursorExpiredReason[] {IDLE_TIMEOUT, SESSION_ENDED, QUICKBOOKS_RESTARTED, EVICTED}) {
            if (r.value.equals(value)) return r;
        }
        return new CursorExpiredReason(value, Known.UNRECOGNIZED);
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean isKnown() {
        return known != Known.UNRECOGNIZED;
    }

    /**
     * The known value, for a {@code switch}.
     *
     * @return the constant, or {@code UNRECOGNIZED}
     */
    public Known known() {
        return known;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CursorExpiredReason && ((CursorExpiredReason) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

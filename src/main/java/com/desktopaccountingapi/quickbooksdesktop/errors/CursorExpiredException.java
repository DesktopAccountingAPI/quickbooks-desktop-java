package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * A cursor list could not continue because its QuickBooks iterator expired ({@code 410
 * CURSOR_EXPIRED}): the idle window passed between pages, the QuickBooks session ended, QuickBooks
 * restarted, or the iterator was evicted ({@link #reason()}).
 *
 * <p>The SDK never restarts the list on its own, because records may have changed in between.
 * The progress fields say how far the iteration got. To resume, start a new list sorted by
 * modification time with {@code updatedAfter} set to {@link #lastUpdatedAt()} (a watermark) and skip
 * IDs you already processed, or restart from the beginning.
 */
public class CursorExpiredException extends InvalidRequestException {
    private static final long serialVersionUID = 1L;
    private final int itemsYielded;
    private final int pagesServed;
    private final String lastId;
    private final String lastUpdatedAt;

    /**
     * Creates the exception without iteration progress (as returned by a single continue request).
     *
     * @param info the error fields
     */
    public CursorExpiredException(ApiErrorInfo info) {
        this(info, 0, pagesServedFrom(info, 0), null, null);
    }

    /**
     * Creates the exception with iteration progress.
     *
     * @param info the error fields
     * @param itemsYielded items delivered to the caller before the error
     * @param pagesServed pages served ({@code details.pagesServed} when present)
     * @param lastId {@code id} of the last delivered item, or null
     * @param lastUpdatedAt {@code updatedAt} of the last delivered item as received, or null
     */
    public CursorExpiredException(ApiErrorInfo info, int itemsYielded, int pagesServed, String lastId, String lastUpdatedAt) {
        super(info);
        this.itemsYielded = itemsYielded;
        this.pagesServed = pagesServed;
        this.lastId = lastId;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    /**
     * Copy of this exception carrying the progress of an iteration.
     *
     * @param itemsYielded items delivered to the caller
     * @param pagesDelivered pages delivered to the caller (used when the body has no
     *     {@code details.pagesServed})
     * @param lastId {@code id} of the last delivered item
     * @param lastUpdatedAt {@code updatedAt} of the last delivered item, as received
     * @return the new exception
     */
    public CursorExpiredException withProgress(int itemsYielded, int pagesDelivered, String lastId, String lastUpdatedAt) {
        CursorExpiredException e = new CursorExpiredException(info(), itemsYielded, pagesServedFrom(info(), pagesDelivered), lastId, lastUpdatedAt);
        e.setStackTrace(getStackTrace());
        return e;
    }

    private static int pagesServedFrom(ApiErrorInfo info, int fallback) {
        Object v = info.details == null ? null : info.details.get("pagesServed");
        return v instanceof Number ? ((Number) v).intValue() : fallback;
    }

    /**
     * Items the iteration delivered before the cursor expired.
     *
     * @return the count
     */
    public int itemsYielded() {
        return itemsYielded;
    }

    /**
     * Pages served before the cursor expired: {@code details.pagesServed} if the API sent it, else the
     * pages the SDK delivered.
     *
     * @return the count
     */
    public int pagesServed() {
        return pagesServed;
    }

    /**
     * {@code id} of the last item delivered.
     *
     * @return the ID, or null if none was delivered
     */
    public String lastId() {
        return lastId;
    }

    /**
     * {@code updatedAt} of the last item delivered, exactly as received. Use it as the
     * {@code updatedAfter} watermark when restarting.
     *
     * @return the timestamp, or null
     */
    public String lastUpdatedAt() {
        return lastUpdatedAt;
    }

    /**
     * Why the cursor expired ({@code details.reason}): {@code idle_timeout}, {@code session_ended},
     * {@code quickbooks_restarted} or {@code evicted}.
     *
     * @return the reason, or null
     */
    public String reason() {
        Object r = details().get("reason");
        return r instanceof String ? (String) r : null;
    }
}

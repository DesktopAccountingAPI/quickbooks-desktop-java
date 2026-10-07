package com.desktopaccountingapi.quickbooksdesktop.core;

import com.desktopaccountingapi.quickbooksdesktop.errors.CursorExpiredException;
import com.desktopaccountingapi.quickbooksdesktop.errors.DaapiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A cursor list. Nothing is fetched until you use it.
 *
 * <pre>{@code
 * // Every invoice, page after page:
 * for (Invoice invoice : client.qbd().invoices().list(new InvoiceListParams().limit(50))) { ... }
 * // Only the first page:
 * Page<Invoice> page = client.qbd().invoices().list().firstPage();
 * }</pre>
 *
 * <p>Iterating ({@link #iterator()}, {@link #stream()}, {@link #pages()}, {@link #listAll()}) starts
 * from the first page each time. The next page is requested only when the iteration needs it, so a
 * loop that stops early never sends an extra QuickBooks query. While you iterate items, a page held
 * for more than 2 seconds makes the pager request the next page in the background, so slow consumers
 * stay inside the cursor's idle window (about 10 seconds). {@link #listAll()} always reads one page
 * ahead. Continue requests send only {@code cursor} (and {@code limit} if you set one). A network
 * error on a continue request retries the same cursor.
 *
 * <p>If the cursor expires, iteration throws {@link CursorExpiredException} with
 * {@code itemsYielded}, {@code pagesServed}, {@code lastId} and {@code lastUpdatedAt}. The SDK never
 * restarts a list on its own: restart it with an {@code updatedAfter} watermark.
 *
 * @param <T> item type
 */
public final class Pager<T> implements Iterable<T> {
    /** How long the item iterator holds a page before it requests the next one in the background. Tests lower it. */
    static volatile long readAheadAfterNanos = 2_000_000_000L;

    private final ClientCore core;
    private final OperationSpec op;
    private final String path;
    private final InputObject query;
    private final RequestOptions options;
    private final Function<Object, T> item;
    private final Object limit;

    Pager(ClientCore core, OperationSpec op, String path, InputObject query, RequestOptions options, Function<Object, T> item) {
        this.core = core;
        this.op = op;
        this.path = path;
        this.query = query;
        this.options = options;
        this.item = item;
        this.limit = query == null ? null : query.toWire().get("limit");
    }

    /**
     * Fetches only the first page (one request, no read-ahead).
     *
     * @return the first page
     */
    public Page<T> firstPage() {
        return core.fetchPage(op, path, query, options, item);
    }

    /**
     * Fetches the page after the given one (one request, no read-ahead).
     *
     * @param page a page from this list
     * @return the next page
     * @throws NoSuchElementException if {@code page} is the last page
     */
    public Page<T> nextPage(Page<T> page) {
        if (!page.hasMore() || page.nextCursor() == null) throw new NoSuchElementException("this is the last page");
        return fetch(page.nextCursor());
    }

    private Page<T> fetch(String cursor) {
        if (cursor == null) return firstPage();
        return core.fetchPage(op, path, new CursorQuery(cursor, limit), options, item);
    }

    /**
     * Iterates page by page; each page is requested when you ask for it.
     *
     * @return the pages
     */
    public Iterable<Page<T>> pages() {
        return PageIterator::new;
    }

    /**
     * Iterates every item across all pages. The next page is requested when the iteration reaches
     * it, or in the background once a page has been held for 2 seconds. {@code hasNext()} can throw
     * the exception of a failed page request, including {@link CursorExpiredException}.
     *
     * @return an item iterator
     */
    @Override
    public Iterator<T> iterator() {
        return new ItemIterator();
    }

    /**
     * Sequential stream of every item across all pages.
     *
     * @return the stream
     */
    public Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false);
    }

    /**
     * Fetches every page as fast as possible and returns all items: each next page is requested in
     * the background as soon as a page arrives. Holds the whole list in memory.
     *
     * @return all items
     */
    public List<T> listAll() {
        List<T> out = new ArrayList<>();
        int pages = 0;
        Object lastRaw = null;
        Page<T> page = fetch(null);
        while (true) {
            final String cursor = page.hasMore() ? page.nextCursor() : null;
            CompletableFuture<Page<T>> pending = cursor == null ? null : CompletableFuture.supplyAsync(() -> fetch(cursor), ClientCore.readAhead());
            out.addAll(page.data());
            pages++;
            if (!page.rawData().isEmpty()) lastRaw = page.rawData().get(page.rawData().size() - 1);
            if (pending == null) return Collections.unmodifiableList(out);
            try {
                page = join(pending);
            } catch (CursorExpiredException e) {
                throw e.withProgress(out.size(), pages, member(lastRaw, "id"), member(lastRaw, "updatedAt"));
            }
        }
    }

    private static String member(Object raw, String key) {
        if (!(raw instanceof Map)) return null;
        Object v = ((Map<?, ?>) raw).get(key);
        return v instanceof String ? (String) v : null;
    }

    private static <P> P join(CompletableFuture<P> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new DaapiException("page request failed", c);
        }
    }

    /** Page iterator; each page is requested by {@code next()}. */
    private final class PageIterator implements Iterator<Page<T>> {
        private boolean started;
        private String cursor;
        private int pagesDelivered;
        private int itemsDelivered;
        private Object lastRaw;

        @Override
        public boolean hasNext() {
            return !started || cursor != null;
        }

        @Override
        public Page<T> next() {
            if (!hasNext()) throw new NoSuchElementException();
            Page<T> page;
            try {
                page = fetch(started ? cursor : null);
            } catch (CursorExpiredException e) {
                throw e.withProgress(itemsDelivered, pagesDelivered, member(lastRaw, "id"), member(lastRaw, "updatedAt"));
            }
            started = true;
            cursor = page.hasMore() ? page.nextCursor() : null;
            pagesDelivered++;
            itemsDelivered += page.data().size();
            if (!page.rawData().isEmpty()) lastRaw = page.rawData().get(page.rawData().size() - 1);
            return page;
        }
    }

    /**
     * Item iterator. Requests the next page when the current one is used up, or in the background
     * once a page has been held for {@link #readAheadAfterNanos}; tracks progress for
     * {@link CursorExpiredException}.
     */
    private final class ItemIterator implements Iterator<T> {
        private boolean started;
        private Page<T> page;
        private int index;
        private long receivedNanos;
        private String cursor;
        private CompletableFuture<Page<T>> pending;
        private int yielded;
        private int pages;
        private Object lastRaw;

        @Override
        public boolean hasNext() {
            while (page == null || index >= page.data().size()) {
                if (started && cursor == null) return false;
                Page<T> p;
                try {
                    if (!started) {
                        p = fetch(null);
                    } else if (pending != null) {
                        CompletableFuture<Page<T>> f = pending;
                        pending = null;
                        p = join(f);
                    } else {
                        p = fetch(cursor);
                    }
                } catch (CursorExpiredException e) {
                    throw e.withProgress(yielded, pages, member(lastRaw, "id"), member(lastRaw, "updatedAt"));
                }
                started = true;
                page = p;
                index = 0;
                pages++;
                receivedNanos = System.nanoTime();
                cursor = p.hasMore() ? p.nextCursor() : null;
            }
            return true;
        }

        @Override
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            // Read-ahead for slow consumers: the caller asked for another item and has held this page
            // long enough that waiting for its end could let the cursor's idle window lapse.
            if (cursor != null && pending == null && System.nanoTime() - receivedNanos >= readAheadAfterNanos) {
                final String c = cursor;
                pending = CompletableFuture.supplyAsync(() -> fetch(c), ClientCore.readAhead());
            }
            T t = page.data().get(index);
            lastRaw = page.rawData().get(index);
            index++;
            yielded++;
            return t;
        }
    }

    /** Continue request: only the cursor and, if the caller set one, the limit. */
    static final class CursorQuery extends InputObject {
        CursorQuery(String cursor, Object limit) {
            setOptional("cursor", cursor);
            setOptional("limit", limit);
        }
    }
}

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
 * from the first page each time. As soon as page N arrives, page N+1 is requested in the background
 * (one page of read-ahead), so the next continue request reaches the server inside the cursor's idle
 * window even while you process page N. Continue requests send only {@code cursor} (and
 * {@code limit} if you set one). A network error on a continue request retries the same cursor.
 *
 * <p>If the cursor expires, iteration throws {@link CursorExpiredException} with
 * {@code itemsYielded}, {@code pagesServed}, {@code lastId} and {@code lastUpdatedAt}. The SDK never
 * restarts a list on its own: restart it with an {@code updatedAfter} watermark.
 *
 * @param <T> item type
 */
public final class Pager<T> implements Iterable<T> {
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
     * Iterates page by page, with one page of read-ahead.
     *
     * @return the pages
     */
    public Iterable<Page<T>> pages() {
        return PageIterator::new;
    }

    /**
     * Iterates every item across all pages, with one page of read-ahead. {@code hasNext()} can throw
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
     * Fetches every page as fast as possible and returns all items. Holds the whole list in memory.
     *
     * @return all items
     */
    public List<T> listAll() {
        List<T> out = new ArrayList<>();
        for (T t : this) out.add(t);
        return Collections.unmodifiableList(out);
    }

    private static String member(Object raw, String key) {
        if (!(raw instanceof Map)) return null;
        Object v = ((Map<?, ?>) raw).get(key);
        return v instanceof String ? (String) v : null;
    }

    /** Page iterator with one page of read-ahead. */
    private final class PageIterator implements Iterator<Page<T>> {
        private boolean started;
        private CompletableFuture<Page<T>> pending;
        private int pagesDelivered;
        private int itemsDelivered;
        private Object lastRaw;

        @Override
        public boolean hasNext() {
            return !started || pending != null;
        }

        @Override
        public Page<T> next() {
            Page<T> page;
            try {
                if (!started) {
                    started = true;
                    page = fetch(null);
                } else {
                    if (pending == null) throw new NoSuchElementException();
                    CompletableFuture<Page<T>> f = pending;
                    pending = null;
                    page = join(f);
                }
            } catch (CursorExpiredException e) {
                throw e.withProgress(itemsDelivered, pagesDelivered, member(lastRaw, "id"), member(lastRaw, "updatedAt"));
            }
            if (page.hasMore() && page.nextCursor() != null) {
                String cursor = page.nextCursor();
                pending = CompletableFuture.supplyAsync(() -> fetch(cursor), ClientCore.readAhead());
            }
            pagesDelivered++;
            itemsDelivered += page.data().size();
            if (!page.rawData().isEmpty()) lastRaw = page.rawData().get(page.rawData().size() - 1);
            return page;
        }

        private Page<T> join(CompletableFuture<Page<T>> f) {
            try {
                return f.join();
            } catch (CompletionException e) {
                Throwable c = e.getCause();
                if (c instanceof RuntimeException) throw (RuntimeException) c;
                if (c instanceof Error) throw (Error) c;
                throw new DaapiException("page request failed", c);
            }
        }
    }

    /** Item iterator over {@link PageIterator}; tracks progress for {@link CursorExpiredException}. */
    private final class ItemIterator implements Iterator<T> {
        private final PageIterator pages = new PageIterator();
        private Iterator<T> current = Collections.emptyIterator();
        private List<Object> currentRaw = Collections.emptyList();
        private int index;
        private int yielded;
        private int pagesStarted;
        private Object lastRaw;

        @Override
        public boolean hasNext() {
            while (!current.hasNext()) {
                if (!pages.hasNext()) return false;
                Page<T> p;
                try {
                    p = pages.next();
                } catch (CursorExpiredException e) {
                    throw e.withProgress(yielded, pagesStarted, member(lastRaw, "id"), member(lastRaw, "updatedAt"));
                }
                pagesStarted++;
                current = p.data().iterator();
                currentRaw = p.rawData();
                index = 0;
            }
            return true;
        }

        @Override
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            T t = current.next();
            lastRaw = currentRaw.get(index++);
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

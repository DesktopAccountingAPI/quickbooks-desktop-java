package com.desktopaccountingapi.quickbooksdesktop.core;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default {@link Transport} built on {@link java.net.http.HttpClient}. Uses HTTP/2 over TLS and
 * HTTP/1.1 for plain {@code http://} URLs (local mock servers), and never follows redirects.
 */
public final class JavaHttpTransport implements Transport {
    private final HttpClient client;

    /** Creates a transport with its own {@link HttpClient} (10 s connect timeout). */
    public JavaHttpTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    /**
     * Creates a transport over a caller-configured client (proxy, SSL context, executor).
     *
     * @param client the HTTP client to use
     */
    public JavaHttpTransport(HttpClient client) {
        if (client == null) throw new IllegalArgumentException("client is null");
        this.client = client;
    }

    @Override
    public Response send(Request request) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(request.uri()).timeout(request.timeout());
        if ("http".equalsIgnoreCase(request.uri().getScheme())) b.version(HttpClient.Version.HTTP_1_1);
        for (Map.Entry<String, String> h : request.headers().entrySet()) b.header(h.getKey(), h.getValue());
        HttpRequest.BodyPublisher body = request.body() == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
        b.method(request.method(), body);
        // HttpRequest.timeout covers only the wait for the response headers: a body that keeps
        // trickling in could hold the calling thread past the attempt's budget. The whole exchange,
        // body included, is bounded by waiting on the async send and canceling it at the timeout.
        CompletableFuture<HttpResponse<String>> pending = client.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> res;
        try {
            res = pending.get(request.timeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new HttpTimeoutException("request timed out after " + request.timeout().toMillis() + " ms (headers and body)");
        } catch (InterruptedException e) {
            pending.cancel(true);
            throw e;
        } catch (CancellationException e) {
            throw new IOException("request canceled", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IOException(cause == null ? "request failed" : cause.toString(), cause);
        }
        return new Response(res.statusCode(), Headers.of(res.headers().map()), res.body());
    }
}

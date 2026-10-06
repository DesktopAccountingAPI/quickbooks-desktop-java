package com.desktopaccountingapi.quickbooksdesktop.core;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

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
        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(res.statusCode(), Headers.of(res.headers().map()), res.body());
    }
}

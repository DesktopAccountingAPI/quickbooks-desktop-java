package com.desktopaccountingapi.quickbooksdesktop.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The attempt timeout bounds the whole exchange, body included (codex re-review #15). */
class JavaHttpTransportTest {
    private HttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Headers at once, then a body that trickles in 25 ms per chunk (about 600 ms in total).
        server.createContext("/slow", exchange -> {
            byte[] body = "{\"id\":\"req_x\",\"status\":\"succeeded\",\"padding\":\"xxxxxxxxxxxxxxxxxxxxxxxx\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                for (byte b : body) {
                    out.write(b);
                    out.flush();
                    Thread.sleep(25);
                }
            } catch (Exception ignored) {
                // The client gave up; nothing to do.
            }
        });
        server.createContext("/fast", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    void slowBodyIsCutOffAtTheTimeout() {
        JavaHttpTransport transport = new JavaHttpTransport();
        Transport.Request request = new Transport.Request("GET", uri("/slow"), Collections.emptyMap(), null, Duration.ofMillis(80));
        long started = System.nanoTime();
        assertThrows(HttpTimeoutException.class, () -> transport.send(request));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(elapsedMs < 400, "the 80 ms budget ended after " + elapsedMs + " ms");
    }

    @Test
    void fastResponseStillWorks() throws Exception {
        JavaHttpTransport transport = new JavaHttpTransport();
        Transport.Response res = transport.send(new Transport.Request("GET", uri("/fast"), Collections.emptyMap(), null, Duration.ofSeconds(5)));
        assertEquals(200, res.status());
        assertEquals("ok", res.body());
    }
}

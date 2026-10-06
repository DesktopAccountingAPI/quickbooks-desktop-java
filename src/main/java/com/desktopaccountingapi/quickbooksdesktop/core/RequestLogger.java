package com.desktopaccountingapi.quickbooksdesktop.core;

/**
 * Receives one line per HTTP attempt, retry and long-poll, for example
 * {@code POST /v1/quickbooks-desktop/invoices -> 201 req_01j9... (attempt 1, 412 ms)}. Lines never
 * contain the secret key, the {@code Authorization} header, query strings or bodies.
 *
 * <p>Example: {@code .logger(line -> System.err.println("[daapi] " + line))}, or forward to SLF4J
 * or {@link System.Logger}.
 */
@FunctionalInterface
public interface RequestLogger {
    /**
     * Handles one log line.
     *
     * @param line the message
     */
    void log(String line);
}

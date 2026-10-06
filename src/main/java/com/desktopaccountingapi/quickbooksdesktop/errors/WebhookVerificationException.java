package com.desktopaccountingapi.quickbooksdesktop.errors;

/**
 * A webhook delivery failed verification: a header is missing, the timestamp is outside the
 * tolerance, no {@code v1} signature matches, the secret is malformed, or the body is not a valid
 * event. Respond with HTTP 400 and do not process the payload.
 */
public class WebhookVerificationException extends DaapiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message why verification failed
     */
    public WebhookVerificationException(String message) {
        super(message);
    }
}

package com.desktopaccountingapi.quickbooksdesktop.webhooks;

/**
 * Known webhook event types. {@link WebhookEvent#type()} is an open set; new types may be added
 * without a new SDK version.
 */
public final class WebhookEventType {
    private WebhookEventType() {}

    /** A request reached {@code succeeded}. */
    public static final String REQUEST_SUCCEEDED = "request.succeeded";
    /** A request reached {@code failed}. */
    public static final String REQUEST_FAILED = "request.failed";
    /** An async request was canceled. */
    public static final String REQUEST_CANCELED = "request.canceled";
    /** A write became {@code outcome_unknown}. */
    public static final String REQUEST_OUTCOME_UNKNOWN = "request.outcome_unknown";
    /** An {@code outcome_unknown} write was resolved by recovery. */
    public static final String REQUEST_OUTCOME_RESOLVED = "request.outcome_resolved";
    /** An auth session finished and the first health check passed. */
    public static final String CONNECTION_SETUP_COMPLETED = "connection.setup_completed";
    /** The derived connection status changed. */
    public static final String CONNECTION_STATUS_CHANGED = "connection.status_changed";
    /** The marker that identifies the connection's company file was created, restored or adopted. */
    public static final String CONNECTION_COMPANY_FILE_REMARKED = "connection.company_file_remarked";
    /** Test event sent from the dashboard or {@code POST /v1/webhook-endpoints/{id}/test}. */
    public static final String WEBHOOK_TEST = "webhook.test";
}

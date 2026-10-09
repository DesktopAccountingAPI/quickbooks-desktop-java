# Desktop Accounting API Java SDK

The Java client for [Desktop Accounting API](https://www.desktopaccountingapi.com/), a REST API for QuickBooks Desktop and QuickBooks Enterprise. Your server makes typed calls such as `client.qbd().invoices().create(...)`, and Desktop Accounting API delivers them to your customer's company file through the QuickBooks Web Connector.

- Covers all 275 operations of API 1.0.0: QuickBooks objects and reports (`client.qbd()`), end users, auth sessions, request tracking, qbXML passthrough and webhook verification.
- Immutable models, fluent inputs, `Iterable` and `Stream` pagination and unchecked typed exceptions for every error type.
- Amounts are `BigDecimal`, sent with their scale preserved.
- Every write carries an idempotency key, retries happen only where they cannot duplicate data, and an expired QuickBooks cursor never restarts a list silently.
- HTTP goes through `java.net.http.HttpClient` and JSON through the SDK's own strict codec. Java 11 or later.

[Documentation](https://www.desktopaccountingapi.com/docs/) · [API reference](https://www.desktopaccountingapi.com/docs/api/reference/) · [Every SDK method](api.md) · [Examples](https://github.com/DesktopAccountingAPI/examples/tree/main/java) · [Changelog](CHANGELOG.md) · [Status](https://status.desktopaccountingapi.com)

## Install

The artifact is `com.desktopaccountingapi:quickbooks-desktop` on [Maven Central](https://central.sonatype.com/artifact/com.desktopaccountingapi/quickbooks-desktop). The current version is **0.5.2**.

Maven:

```xml skip
<dependency>
  <groupId>com.desktopaccountingapi</groupId>
  <artifactId>quickbooks-desktop</artifactId>
  <version>0.5.2</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin skip
implementation("com.desktopaccountingapi:quickbooks-desktop:0.5.2")
```

Gradle (Groovy DSL):

```groovy skip
implementation 'com.desktopaccountingapi:quickbooks-desktop:0.5.2'
```

## Requirements

- Java 11 or later.
- A server-side application. Secret keys must never reach a browser, an Android app or a desktop app you distribute (see [Authentication](#authentication)).

## Authentication

1. Sign in to the [dashboard](https://www.desktopaccountingapi.com/dashboard) and open **API keys**.
2. Create a secret key. Test projects issue `sk_test_...` keys; production projects issue `sk_live_...` keys. Choose **Read-only** for reporting jobs and AI agents that must never change data. The full key is shown once.
3. Put it in the `DAAPI_SECRET_KEY` environment variable of your server (or your secret store):

```sh
export DAAPI_SECRET_KEY="sk_test_..."
```

`DesktopAccountingApiClient.fromEnv()` reads `DAAPI_SECRET_KEY` (and `DAAPI_BASE_URL`, if set); `DesktopAccountingApiClient.builder().apiKey(...)` takes it explicitly. The SDK checks the key's format and checksum locally, so a mistyped key fails before any network call.

A secret key can read and write every connected company file in its project. Keep it on your server, in a secret manager or environment variable. Never ship it to a browser or client app, and never commit it. The API refuses browser requests from other origins on purpose. If a key leaks, revoke it in the dashboard and create a new one. See [Authentication and API keys](https://www.desktopaccountingapi.com/docs/get-started/authentication/).

## Quickstart

Each of your customers is an **end user** (`eu_...`) with one QuickBooks Desktop company file, connected through the Web Connector. Copy an end user ID from the dashboard's **End users** page and set it as `DAAPI_END_USER_ID` (`export DAAPI_END_USER_ID="eu_..."`), then:

```java run=quickstart harness=none
import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import com.desktopaccountingapi.quickbooksdesktop.models.HealthCheck;
import com.desktopaccountingapi.quickbooksdesktop.models.Invoice;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceListParams;

public class Quickstart {
    public static void main(String[] args) {
        // Reads DAAPI_SECRET_KEY. forEndUser sends Daapi-End-User-Id on every QuickBooks call.
        DesktopAccountingApiClient client = DesktopAccountingApiClient.fromEnv().forEndUser(System.getenv("DAAPI_END_USER_ID"));

        HealthCheck health = client.qbd().healthCheck();
        System.out.println("QuickBooks connection: " + health.status());

        // The loop fetches further pages as needed (10 invoices per request); stop after the first 10.
        int shown = 0;
        for (Invoice invoice : client.qbd().invoices().list(new InvoiceListParams().limit(10))) {
            System.out.println(invoice.refNumber() + " " + invoice.subtotal()); // subtotal is a BigDecimal, for example 105.50
            if (++shown == 10) break;
        }
    }
}
```

The client is immutable and thread-safe; create one and share it. Resource methods mirror the API's operation IDs: `client.qbd().invoices().list/retrieve/create/update/delete(...)`, `client.qbd().reports().generalSummary(...)`, `client.endUsers().create(...)`, `client.authSessions().create(...)`, `client.requests().retrieve(...)`. Because `void` is a Java keyword, the void operations are named `voidTransaction(...)`:

```java
client.qbd().invoices().voidTransaction("7-1700000000");
```

## End users

QuickBooks Desktop operations (`client.qbd()...`) act on one end user's company file and send the `Daapi-End-User-Id` header. Set a default on the builder, derive a client per end user, or pass it per call:

```java
DesktopAccountingApiClient acme = client.forEndUser("eu_01j9x4m6v4c8k2t7q0r5s3w1zb"); // shares the HTTP transport
acme.qbd().customers().retrieve("80000001-1700000000");
client.qbd().healthCheck(RequestOptions.endUser("eu_01j9x4m6v4c8k2t7q0r5s3w1zb")); // or per call
```

Without an end user, QuickBooks calls throw `DaapiException` before anything is sent. Platform operations (`endUsers()`, `authSessions()`, `requests()`) never send the header. Create end users and their setup links with `client.endUsers().create(...)` and `client.authSessions().create(...)`; see [End users](https://www.desktopaccountingapi.com/docs/connect/end-users/).

## Common workflows

### List records with auto-pagination

Iterating a list walks every page. The next page is requested only when the loop needs it, so a loop that stops early never runs an extra QuickBooks query. If you hold a page for more than 2 seconds, the SDK requests the next one in the background, so slow loop bodies stay inside the QuickBooks cursor's idle window.

```java
for (Customer customer : client.qbd().customers().list(new CustomerListParams().limit(100).updatedAfter("2026-01-01"))) {
    System.out.println(customer.id() + " " + customer.fullName() + " " + customer.balance());
}

Page<Customer> page = client.qbd().customers().list(new CustomerListParams().limit(100)).firstPage(); // only the first page
System.out.println(page.data().size() + " " + page.hasMore() + " " + page.nextCursor());
```

More options, and what to do when a cursor expires, are in [Pagination](#pagination).

### Create a record with an idempotency key

Every write sends an `Idempotency-Key`. Pass your own, derived from your data, so a retry after a crash or timeout returns the first result instead of creating a duplicate:

```java
Invoice invoice = client.qbd().invoices().create(
    new InvoiceCreateInput("80000001-1700000000")              // required: customerId
        .transactionDate(LocalDate.of(2026, 10, 5))
        .refNumber("WEB-8812")
        .lines(List.of(new InvoiceLineCreateInput()
            .itemId("80000005-1700000000")
            .quantity(2)
            .rate(new BigDecimal("52.75")))),
    RequestOptions.builder().idempotencyKey("order-8812-invoice").build());
System.out.println(invoice.id() + " " + invoice.refNumber() + " " + invoice.subtotal()); // subtotal 105.50
```

### Update a record with its revision number

QuickBooks rejects an update unless it carries the object's current `revisionNumber`, so concurrent edits are never overwritten. Read the object, then send its `revisionNumber` with only the fields you change:

```java
Invoice current = client.qbd().invoices().retrieve("7-1700000000");
try {
    Invoice updated = client.qbd().invoices().update(current.id(),
        new InvoiceUpdateInput(current.revisionNumber())   // required: revisionNumber
            .memo("Paid by card"));
    System.out.println(updated.revisionNumber());          // the new revision
} catch (IntegrationException e) {
    if (!ErrorCode.QBD_REVISION_NUMBER_STALE.equals(e.code())) throw e;
    // Someone changed the invoice after you read it. Retrieve it again, reapply your change,
    // and update with the new revisionNumber.
}
```

A stale revision is a `409` `INTEGRATION_ERROR` with code `QBD_REVISION_NUMBER_STALE`. Nothing was changed (`outcome: "not_applied"`):

```json
{
  "error": {
    "type": "INTEGRATION_ERROR",
    "code": "QBD_REVISION_NUMBER_STALE",
    "message": "The object changed since you read it; revisionNumber is out of date.",
    "userFacingMessage": "This record changed in QuickBooks Desktop after it was loaded. Reload it and try again.",
    "httpStatusCode": 409,
    "integrationCode": "3200",
    "requestId": "req_01j9x4m6v4c8k2t7q0r5s3w1zd",
    "cause": "QuickBooks rejects updates that do not carry the current revision number, so concurrent edits are not lost.",
    "fixes": [{ "actor": "developer", "action": "Retrieve the object, merge your change, and update with the new revisionNumber." }],
    "docsUrl": "https://www.desktopaccountingapi.com/docs/errors/#qbd_revision_number_stale",
    "retryable": false,
    "outcome": "not_applied",
    "param": null,
    "details": {}
  }
}
```

### Handle errors

Exceptions are typed by the API's error `type`, and every API error carries the request ID, a message you can show your end user, the cause, concrete fixes and a link to its documentation:

```java
try {
    client.qbd().customers().retrieve("80000099-1700000000");
} catch (IntegrationConnectionException e) {
    // QuickBooks is closed, a dialog is open, or the Web Connector is not running: the end user has to act.
    showToEndUser(e.userFacingMessage() != null ? e.userFacingMessage() : e.getMessage());
} catch (ApiException e) {
    System.err.println(e.status() + " " + e.code() + ": " + e.getMessage() + " (request " + e.requestId() + ")");
    System.err.println(e.errorCause() + " " + e.docsUrl());
    e.fixes().forEach(f -> System.err.println(f.actor() + ": " + f.action()));
}
```

Every class and accessor is listed in [Errors](#errors). The [error catalog](https://www.desktopaccountingapi.com/docs/errors/) documents every code.

### Run a request asynchronously and get a webhook

QuickBooks only processes requests while the end user's Web Connector is running. `enqueue()` queues a request and returns at once with a handle; the API also sends a `request.succeeded` or `request.failed` webhook when it finishes:

```java
RequestHandle<Invoice> handle = client.qbd().invoices().enqueue().create(
    new InvoiceCreateInput("80000001-1700000000"),
    RequestOptions.builder().queueTtl(Duration.ofHours(1)).idempotencyKey("order-8813-invoice").build());
System.out.println(handle.id() + " is " + handle.request().status()); // req_... is queued
Invoice invoice = handle.await(Duration.ofMinutes(2));               // the typed Invoice, or the typed exception
System.out.println(invoice.refNumber());
```

Verify each webhook delivery with the endpoint's signing secret before you trust it. Pass the raw body, not parsed JSON:

```java harness=webhook
try {
    WebhookEvent event = Webhooks.verify(rawBody, requestHeaders, System.getenv("DAAPI_WEBHOOK_SECRET"));
    if (WebhookEventType.REQUEST_SUCCEEDED.equals(event.type())) {
        Request request = client.requests().retrieve((String) event.data().get("id"));
        System.out.println(request.id() + " succeeded");
    }
    return 204;
} catch (WebhookVerificationException e) {
    return 400;
}
```

`requestHeaders` is a `Map<String, ?>` with `String` or `List<String>` values, as most frameworks provide. Create webhook endpoints and copy their `whsec_...` signing secrets in the dashboard under **Webhooks**. Details: [Async requests](#async-requests), [Webhooks](#webhooks), and the [webhooks guide](https://www.desktopaccountingapi.com/docs/guides/webhooks/).

### Set timeouts and retries

```java
DesktopAccountingApiClient patient = DesktopAccountingApiClient.builder()
    .timeout(Duration.ofSeconds(30))
    .maxRetries(4)
    .serverTimeout(Duration.ofSeconds(25))
    .build();
patient.qbd().invoices().retrieve("7-1700000000",
    RequestOptions.builder().endUserId("eu_01j9x4m6v4c8k2t7q0r5s3w1zb").maxRetries(0).build());
```

`timeout` is the client's limit per HTTP attempt; `totalTimeout` caps a whole call including retries; `serverTimeout` is how long the API waits for QuickBooks. Reads and writes retry only when it is safe; see [Retries and idempotency](#retries-and-idempotency) and [Timeouts](#timeouts).

## Configuration

| Builder method | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `apiKey(String)` | `DAAPI_SECRET_KEY` | required | Secret key. Its format and checksum are checked locally before the first request; a missing or malformed key throws `DaapiException`. |
| `baseUrl(String)` | `DAAPI_BASE_URL` | `https://api.desktopaccountingapi.com` | API base URL. May include a path; the SDK appends `/v1/...`. A trailing `/v1` is removed, so `https://api.desktopaccountingapi.com/v1` works too. |
| `endUserId(String)` | | none | Default end user for QuickBooks Desktop operations. |
| `timeout(Duration)` | | 100 s | Client-side timeout per HTTP attempt (server default 90 s plus 10 s); each retry gets a fresh one. |
| `totalTimeout(Duration)` | | none | Time budget of a whole call: attempts, retry backoff and the wait for a pending request. |
| `maxRetries(int)` | | 2 | Retries after network errors, 429 and retryable 5xx responses. |
| `serverTimeout(Duration)` | | server default | How long the API waits for QuickBooks (`Daapi-Timeout-Seconds`, 1 to 300 s). |
| `defaultHeader(String, String)` / `defaultHeaders(Map)` | | none | Headers sent with every request. The headers the SDK manages (`Authorization`, `Accept`, `Content-Type`, `User-Agent`, `Daapi-End-User-Id`, `Idempotency-Key`, `Daapi-Timeout-Seconds`, `Prefer`) are ignored here. |
| `transport(Transport)` / `httpClient(HttpClient)` | | `JavaHttpTransport` | HTTP layer: proxies, custom TLS, instrumentation, test doubles. |
| `logger(RequestLogger)` | | none | One line per attempt and retry. Never includes keys, headers or bodies. |

Every method has an overload taking `RequestOptions` for per-call overrides:

```java
RequestOptions opts = RequestOptions.builder()
    .endUserId("eu_01j9x4m6v4c8k2t7q0r5s3w1zb")
    .idempotencyKey("order-8812-invoice")
    .timeout(Duration.ofSeconds(30))
    .totalTimeout(Duration.ofMinutes(2))
    .maxRetries(0)
    .serverTimeout(Duration.ofSeconds(60))
    .build();
client.qbd().invoices().retrieve("7-1700000000", opts);
```

## Models and types

- Response models are immutable with accessor methods (`invoice.refNumber()`). Unknown fields are kept in `additionalProperties()`; missing fields read as `null`. `toJson()` writes the wire JSON back.
- Money and other decimal fields are `BigDecimal` and travel as decimal strings with their scale (`new BigDecimal("5.00")` is sent as `"5.00"`). Quantities and rates without fixed precision are `Double`.
- Dates are `LocalDate`; timestamps are `OffsetDateTime` with the offset QuickBooks reported. Timestamps are always sent with seconds (`2026-10-05T09:14:00-07:00`).
- Filters such as `updatedAfter` accept a `String`, `LocalDate` or `OffsetDateTime`.
- Enums are open: fields are `String`, and the known values are constants (`DateMacro.THIS_MONTH`, `RequestStatus.SUCCEEDED`). Values added to the API later pass through unchanged.
- Input objects have fluent setters. Fields you never set are not sent. On clearable fields, `null` sends JSON `null` and clears the value in QuickBooks.
- Required fields are checked before the request is sent, not by the compiler: a required field you never set throws `DaapiException` and nothing is sent. The constructor that takes the required fields (`new InvoiceCreateInput(customerId)`) is the way to have the compiler check them:

```java
Invoice invoice = client.qbd().invoices().retrieve("7-1700000000");
client.qbd().invoices().update(invoice.id(),
    new InvoiceUpdateInput(invoice.revisionNumber())   // required: revisionNumber
        .memo(null));                                   // {"revisionNumber":"...","memo":null}
```

## Pagination

Cursor lists return a `Pager<T>`. Nothing is fetched until you use it.

```java
Pager<Customer> customers = client.qbd().customers().list(new CustomerListParams().limit(100));

for (Customer c : customers) process(c);                         // every item, page after page
long active = customers.stream().filter(c -> Boolean.TRUE.equals(c.isActive())).count(); // java.util.stream
List<Customer> all = customers.listAll();                        // everything in memory
Page<Customer> first = customers.firstPage();                    // one request
System.out.println(first.data().size() + " " + first.nextCursor() + " " + first.hasMore() + " " + first.remainingCount() + " " + first.cursorExpiresAt());
for (Page<Customer> page : customers.pages()) System.out.println(page.data().size());
System.out.println(active + " of " + all.size());
```

The next page is requested only when the iteration reaches it, so `break`ing out of a loop never sends an extra QuickBooks query. While you iterate items, a page held for more than 2 seconds makes the SDK request the next page in the background, which keeps slow loops inside the cursor's idle window. `pages()` requests each page when you ask for it; `listAll()` always requests the next page as soon as a page arrives. A network error on a continue request retries the same cursor, which returns the same page.

To resume from a page you stored earlier, for example across HTTP requests, pass its `nextCursor()` as `cursor`: `client.qbd().invoices().list(new InvoiceListParams().cursor(savedCursor).limit(100)).firstPage()` returns that page, and iterating continues from it. Filters live in the cursor, so pass only the cursor and, if you like, the limit. A QuickBooks cursor expires when it sits idle, so resume soon after you store it.

A QuickBooks cursor expires when no continue request arrives within its idle window, or when the QuickBooks session ends. Iteration then throws `CursorExpiredException` (a subclass of `InvalidRequestException`). The SDK never restarts a list on its own, because records may have changed. Restart the same query and skip what you already have. Do not resume from the last record's `updatedAt`: QuickBooks returns records in its own order, not by `updatedAt`, so records you have not read yet can be older than the last one you read. An incremental sync restarts from the `updatedAfter` watermark it saved before the traversal ([pagination guide](https://www.desktopaccountingapi.com/docs/guides/pagination/#recovering-from-cursor_expired)).

```java
import java.util.HashSet;
import java.util.Set;

Set<String> seen = new HashSet<>();
try {
    for (Customer c : client.qbd().customers().list(new CustomerListParams().limit(100))) {
        process(c);
        seen.add(c.id());
    }
} catch (CursorExpiredException e) {
    System.out.println(e.itemsYielded() + " items, " + e.pagesServed() + " pages, last " + e.lastId() + ", reason " + e.reason() + " (" + e.requestId() + ")");
    // Restart the same query and skip the IDs you already have.
    for (Customer c : client.qbd().customers().list(new CustomerListParams().limit(100))) {
        if (seen.add(c.id())) process(c);
    }
}
```

Lists without an iterator in QuickBooks (accounts, classes, terms and other small lists) return their list object directly, for example `AccountList` with `data()`. The [pagination guide](https://www.desktopaccountingapi.com/docs/guides/pagination/) explains cursor lifetimes.

## Errors

Every exception extends `DaapiException` (unchecked).

| Exception | When |
| --- | --- |
| `DaapiException` | Client-side problems: missing or malformed key, no end user, required field not set, unparseable response. |
| `ApiException` | Any API error response. Base class for the types below; thrown as-is for unknown error types and non-JSON bodies. |
| `InvalidRequestException` | `INVALID_REQUEST_ERROR` |
| `CursorExpiredException` | `CURSOR_EXPIRED` (subclass of `InvalidRequestException`) |
| `AuthenticationException` | `AUTHENTICATION_ERROR` |
| `PermissionException` | `PERMISSION_ERROR` |
| `BillingException` | `BILLING_ERROR` |
| `RateLimitException` | `RATE_LIMIT_ERROR` |
| `IntegrationConnectionException` | `INTEGRATION_CONNECTION_ERROR`: QuickBooks closed, dialog open, Web Connector offline |
| `IntegrationException` | `INTEGRATION_ERROR`: QuickBooks rejected the request |
| `OutcomeUnknownException` | `OUTCOME_UNKNOWN_ERROR`: a write may or may not have been applied |
| `InternalException` | `INTERNAL_ERROR` |
| `ApiConnectionException` / `ApiTimeoutException` | No response after all retries |
| `RequestPendingException` | The call's time budget ran out while the request was still queued or running |
| `WebhookVerificationException` | Webhook signature or timestamp check failed |

Exceptions are in `com.desktopaccountingapi.quickbooksdesktop.errors`. `ApiException` exposes every field of the error body: `status()`, `type()`, `code()`, `getMessage()`, `userFacingMessage()`, `httpStatusCode()`, `integrationCode()`, `requestId()` (falls back to the `Daapi-Request-Id` header; `getRequestId()` is the same value), `errorCause()` (the API's `cause`; `getCause()` is the Java exception chain), `fixes()` (`actor()`, `action()`), `docsUrl()`, `retryable()`, `outcome()`, `param()`, `details()` and `headers()`. Codes are constants in `models.ErrorCode`, types in `models.ErrorType`:

```java
try {
    client.qbd().customers().retrieve("80000099-1700000000");
} catch (IntegrationException e) {
    if (ErrorCode.QBD_OBJECT_NOT_FOUND.equals(e.code())) System.out.println("not found");
    System.out.println(e.userFacingMessage() + " " + e.docsUrl() + " " + e.requestId());
    e.fixes().forEach(f -> System.out.println(f.actor() + ": " + f.action()));
} catch (IntegrationConnectionException e) {
    // The end user has to act (open QuickBooks, close a dialog).
    showToEndUser(e.userFacingMessage());
}
```

`getMessage()` is the API's message only. `toString()`, which loggers and uncaught-exception output use, adds the HTTP status, the code and the request ID, for example `com.desktopaccountingapi.quickbooksdesktop.errors.IntegrationException: 404 QBD_OBJECT_NOT_FOUND The QuickBooks object does not exist. (req_01j9x4m6v4c8k2t7q0r5s3w1zd)`. Log the exception itself (`logger.error("call failed", e)` or `e.toString()`), not only `getMessage()`, so the request ID is kept.

Include the `requestId()` when you contact support. See the [error handling guide](https://www.desktopaccountingapi.com/docs/guides/error-handling/).

## Retries and idempotency

- Retried: network errors before a response (connection failure, reset, timeout) for reads and writes, `429`, and `5xx` responses with `Daapi-Should-Retry: true`, up to `maxRetries` (default 2).
- Never retried: `Daapi-Should-Retry: false`, and errors whose `outcome` is `unknown` or `pending`.
- Backoff: 0.5 s, 1 s, 2 s ... up to 8 s with jitter. `Retry-After` (seconds or HTTP date, including `0`) is honored; a value above 60 s throws instead of waiting.
- Every write sends an `Idempotency-Key`: yours (`RequestOptions.idempotencyKey`) or a UUID generated once per call and reused on every retry of that call, so a retried write is applied once. See the [idempotency guide](https://www.desktopaccountingapi.com/docs/guides/idempotency/).

## Timeouts

There are three timeouts:

- `timeout` (client side, default 100 s) limits each HTTP attempt. A retry starts a new attempt with a fresh timeout, so with retries a call can take longer.
- `totalTimeout` (client side, no default) limits the whole call: every attempt, the waits between retries and the wait for a pending request. An attempt still running when it ends is cut off (`ApiTimeoutException`), and no retry starts that could not finish in time.
- `serverTimeout` (`Daapi-Timeout-Seconds`) is how long the API waits for QuickBooks before it answers. When it is longer than `timeout`, each attempt waits `serverTimeout + 10 s`.

When the API answers `504 QBD_REQUEST_TIMEOUT` (the request reached QuickBooks but has not finished), the SDK does not resubmit. It long-polls `GET /v1/requests/{id}?waitSeconds=...` until the call's time budget (`totalTimeout`, else `timeout`) is used, then returns the result, throws the request's typed error, or throws `RequestPendingException` with `requestId()`. It also throws `RequestPendingException`, never the poll's own exception, when a poll fails (`429`, `5xx`, `404`, network): that failure says nothing about the write. `timeoutError()` is the original 504 and `idempotencyKey()` the key the write was sent with; resend only with that key. The request keeps running; look it up later:

```java
try {
    client.qbd().invoices().create(new InvoiceCreateInput("80000001-1700000000"),
        RequestOptions.builder().idempotencyKey("order-8814-invoice").build());
} catch (RequestPendingException e) {
    Request request = client.requests().retrieve(e.requestId());
    System.out.println(request.status()); // still "queued" or "running"; a webhook reports the result
}
```

## Async requests

Operations that support async mode are also available under `enqueue()`. They return a `RequestHandle<T>` as soon as the API queued the request (`Prefer: respond-async`, `202 Accepted`):

```java
RequestHandle<Invoice> handle = client.qbd().invoices().enqueue().create(new InvoiceCreateInput("80000001-1700000000"),
    RequestOptions.builder().queueTtl(Duration.ofHours(1)).build());
System.out.println(handle.id() + " " + handle.request().status()); // req_... queued
Request current = handle.status();                       // GET /v1/requests/{id}, current Request
Invoice invoice = handle.await(Duration.ofMinutes(10));  // long-polls; throws the typed error on failure
Invoice now = handle.result();                           // one read; RequestPendingException if not finished
System.out.println(current.status() + " " + invoice.id() + " " + now.id());
```

(`Object.wait()` is final in Java, so the long-poll method is `await`.) Completion is also delivered by webhook. See the [request lifecycle guide](https://www.desktopaccountingapi.com/docs/guides/request-lifecycle/).

## Webhooks

Deliveries follow [Standard Webhooks](https://www.standardwebhooks.com/). `Webhooks.verify(rawBody, headers, secret)` checks the signature and timestamp and returns the parsed `WebhookEvent`; no API key is needed. Header names are matched case-insensitively. The secret is accepted with or without the `whsec_` prefix. Signatures rotate by sending several `v1,...` values; any match passes. The timestamp must be within 5 minutes of your clock in either direction; `WebhookVerifier.builder().tolerance(...).clock(...)` changes both (the clock is a `java.time.Clock`, for example `Clock.fixed(...)` in tests). `client.webhooks()` returns the default verifier, and `Webhooks.verifySignature(...)` checks only the signature. Delivery is at least once: deduplicate on `event.id()`.

## Raw responses

Non-list methods have a `...WithResponse` variant returning `ApiResponse<T>` with the parsed result, HTTP status and headers:

```java
ApiResponse<Customer> r = client.qbd().customers().retrieveWithResponse("80000001-1700000000");
System.out.println(r.data().fullName() + " " + r.statusCode() + " " + r.requestId() + " " + r.headers().get("Daapi-Warnings"));
```

`r.requestId()` is the ID of the request that produced the result. After the SDK long-polled a request that timed out on the server (`504 QBD_REQUEST_TIMEOUT`), it is that request's ID, which `client.requests().retrieve(r.requestId())` finds; the final poll's own ID stays in the `r.headers().get("Daapi-Request-Id")` header.

## Passthrough

Send qbXML request elements directly, as JSON or as raw XML:

```java
PassthroughResponse json = client.endUsers().passthrough("eu_01j9x4m6v4c8k2t7q0r5s3w1zb",
    new PassthroughInput().put("CustomerQueryRq", Map.of("MaxReturned", 5)));
Object rs = json.get("CustomerQueryRs");

String xml = client.endUsers().passthroughXml("eu_01j9x4m6v4c8k2t7q0r5s3w1zb",
    "<QBXMLMsgsRq onError=\"stopOnError\"><CustomerQueryRq><MaxReturned>5</MaxReturned></CustomerQueryRq></QBXMLMsgsRq>");
System.out.println(rs + " " + xml);
```

## Porting from Conductor

Conductor publishes no Java SDK, so Java code written against Conductor calls its REST API directly. This SDK sends the same paths, parameters and JSON field names; the table maps the Conductor pieces to the SDK.

| Conductor (REST or `conductor-node`) | This SDK |
| --- | --- |
| `https://api.conductor.is/v1` base URL | `baseUrl(...)` (a trailing `/v1` is accepted) |
| `Authorization: Bearer sk_conductor_...` | `apiKey(...)` or `DAAPI_SECRET_KEY` (`sk_test_...`, `sk_live_...`) |
| `Conductor-End-User-Id` header, `conductorEndUserId` parameter | `endUserId(...)` on the builder, `client.forEndUser(...)` or `RequestOptions.endUser(...)` |
| `Conductor-Timeout-Seconds` header | `serverTimeout(...)` |
| `timeout`, `maxRetries` | `timeout(...)` (per attempt), `maxRetries(...)`; plus `totalTimeout(...)` for the whole call |
| `defaultHeaders`, custom `fetch` | `defaultHeader(...)` / `defaultHeaders(...)`, `httpClient(...)` / `transport(...)` |
| `logLevel` / `logger` | `logger(RequestLogger)` |
| `nextCursor` loops | iterate the `Pager<T>`; the next page is requested only when needed |
| Error body `error.code`, `type`, `userFacingMessage`, `httpStatusCode`, `integrationCode`, `requestId` | `ApiException.code()`, `type()`, `userFacingMessage()`, `httpStatusCode()`, `integrationCode()`, `requestId()`, plus `errorCause()`, `fixes()`, `docsUrl()`, `outcome()`, `retryable()` |
| `NotFoundError`, `BadRequestError` and other status classes | the exception for the error `type`; check `ApiException.status()` when you need the HTTP status |

```java
// Before: java.net.http calls to https://api.conductor.is/v1 with Conductor-End-User-Id.
DesktopAccountingApiClient conductor = DesktopAccountingApiClient.builder()
    .baseUrl("https://api.desktopaccountingapi.com/v1")
    .timeout(Duration.ofSeconds(120))
    .maxRetries(2)
    .defaultHeader("X-Trace-Id", "billing-sync")
    .build();
DesktopAccountingApiClient endUser = conductor.forEndUser("eu_01j9x4m6v4c8k2t7q0r5s3w1zb");
try {
    for (Invoice invoice : endUser.qbd().invoices().list(new InvoiceListParams().limit(50))) {
        System.out.println(invoice.refNumber() + " " + invoice.subtotal());
    }
    endUser.qbd().invoices().retrieve("7-1700000000");
} catch (ApiException e) {
    if (e.status() != null && e.status() == 404) {
        System.out.println("Not found: " + e.code() + " (request " + e.requestId() + ")");
    } else {
        showToEndUser(e.userFacingMessage() != null ? e.userFacingMessage() : e.getMessage());
        System.out.println(e.type() + " " + e.code() + " " + e.httpStatusCode() + " " + e.integrationCode() + " " + e.requestId());
    }
}
```

What changes beyond names: every write carries an `Idempotency-Key`, only safe failures are retried (see [Retries and idempotency](#retries-and-idempotency)), and end-user IDs are ours (`eu_...`). The [migration guide](https://www.desktopaccountingapi.com/docs/get-started/migrating-from-conductor/) covers the API-level differences.

## Versioning and changelog

- The SDK follows [semantic versioning](https://semver.org/). Before 1.0, a minor version may contain breaking changes; they are marked Breaking in the [CHANGELOG](https://github.com/DesktopAccountingAPI/quickbooks-desktop-java/blob/main/CHANGELOG.md).
- The Java, Node.js, Python and .NET SDKs and the [MCP server](https://github.com/DesktopAccountingAPI/quickbooks-desktop-mcp) are released together with the same version number, generated from the same API contract.
- Every release is listed in [CHANGELOG.md](CHANGELOG.md) and tagged `v<version>` on GitHub.
- The API is versioned in its path (`/v1`). Within `v1` the API only adds operations, fields, enum values and error codes, which do not break existing code.
- `.daapi-sdk.json` records the contract's SHA-256 (`09aa9517f466...` for this release), and `SdkInfo.VERSION`, `SdkInfo.API_VERSION` and `SdkInfo.CONTRACT_SHA256` expose the same at runtime.

## Support

- [Documentation](https://www.desktopaccountingapi.com/docs/), the [API reference](https://www.desktopaccountingapi.com/docs/api/reference/) and the [error catalog](https://www.desktopaccountingapi.com/docs/errors/).
- [Status page](https://status.desktopaccountingapi.com) for API and connection incidents.
- SDK bugs and feature requests: [GitHub issues](https://github.com/DesktopAccountingAPI/quickbooks-desktop-java/issues).
- Questions about your account, keys, billing or a specific end user's connection: [contact us](https://www.desktopaccountingapi.com/contact). Include the `requestId()` of a failing call, never your secret key.
- Security reports: use **Report a vulnerability** on this repository's Security tab.

## Development

```sh
mise install
mise run check   # build with -Xlint:all -Werror for Java 11, unit tests, conformance suite, javadoc, examples, README samples, Central bundle dry run
```

To build from source, clone the repository and run `mvn -B install -DskipTests`; that puts `com.desktopaccountingapi:quickbooks-desktop:0.5.2` into your local Maven repository. Code under `src/main/java/com/desktopaccountingapi/quickbooksdesktop/{models,services}`, `DesktopAccountingApiClient.java` and this README are generated; `mise run check` compiles every Java sample in this README with `-Xlint:all -Werror` and runs the quickstart against the conformance mock server. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).

QuickBooks is a registered trademark of Intuit Inc. Desktop Accounting API is an independent product and is not affiliated with, endorsed by, or approved by Intuit Inc.

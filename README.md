# Desktop Accounting API Java SDK

Java client for the [Desktop Accounting API](https://www.desktopaccountingapi.com), a REST API for QuickBooks Desktop. It covers all 275 operations of API version 1.0.0 with typed models, auto-pagination, retries with idempotency keys, async requests and webhook verification. The SDK has no runtime dependencies: HTTP goes through `java.net.http.HttpClient` and JSON through the SDK's own strict codec.

- Documentation: https://www.desktopaccountingapi.com/docs/
- Every method with its HTTP route: [api.md](api.md)
- Runnable programs: [DesktopAccountingAPI/examples](https://github.com/DesktopAccountingAPI/examples) (`java/`)

## Requirements

Java 11 or later.

## Install

Maven:

```xml
<dependency>
  <groupId>com.desktopaccountingapi</groupId>
  <artifactId>quickbooks-desktop</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.desktopaccountingapi:quickbooks-desktop:0.1.0")
```

### Install from source

```sh
git clone https://github.com/DesktopAccountingAPI/quickbooks-desktop-java.git
cd quickbooks-desktop-java
mvn -B install -DskipTests
```

This puts `com.desktopaccountingapi:quickbooks-desktop:0.1.0` into your local Maven repository (`~/.m2`), where the dependency above resolves it.

## Quickstart

```java
import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import com.desktopaccountingapi.quickbooksdesktop.models.Invoice;
import com.desktopaccountingapi.quickbooksdesktop.models.InvoiceListParams;

DesktopAccountingApiClient client = DesktopAccountingApiClient.builder()
    .apiKey(System.getenv("DAAPI_SECRET_KEY"))      // sk_test_... or sk_live_...
    .endUserId("eu_01j9x4m6v4c8k2t7q0r5s3w1zb")     // whose QuickBooks company file to use
    .build();

System.out.println(client.qbd().healthCheck().status());

for (Invoice invoice : client.qbd().invoices().list(new InvoiceListParams().limit(50))) {
    System.out.println(invoice.refNumber() + " " + invoice.subtotal());
}
```

`DesktopAccountingApiClient.fromEnv()` reads `DAAPI_SECRET_KEY` and `DAAPI_BASE_URL`. The client is immutable and thread-safe; create one and share it.

Resource methods mirror the API's operation IDs: `client.qbd().invoices().list/retrieve/create/update/delete(...)`, `client.qbd().reports().generalSummary(...)`, `client.endUsers().create(...)`, `client.authSessions().create(...)`, `client.requests().retrieve(...)`. Because `void` is a Java keyword, the void operations are named `voidTransaction(...)`:

```java
client.qbd().invoices().voidTransaction("7-1700000000");
```

## Configuration

| Builder method | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `apiKey(String)` | `DAAPI_SECRET_KEY` | required | Secret key. Its format and checksum are checked locally before the first request; a missing or malformed key throws `DaapiException`. |
| `baseUrl(String)` | `DAAPI_BASE_URL` | `https://api.desktopaccountingapi.com` | API base URL. May include a path; the SDK appends `/v1/...`. Staging: `https://api-staging.desktopaccountingapi.com`. |
| `endUserId(String)` | | none | Default end user for QuickBooks Desktop operations. |
| `timeout(Duration)` | | 100 s | Client-side timeout per HTTP attempt (server default 90 s plus 10 s). |
| `maxRetries(int)` | | 2 | Retries after network errors, 429 and retryable 5xx responses. |
| `serverTimeout(Duration)` | | server default | How long the API waits for QuickBooks (`Daapi-Timeout-Seconds`, 1 to 300 s). |
| `transport(Transport)` / `httpClient(HttpClient)` | | `JavaHttpTransport` | HTTP layer: proxies, custom TLS, instrumentation, test doubles. |
| `logger(RequestLogger)` | | none | One line per attempt and retry. Never includes keys, headers or bodies. |

Every method has an overload taking `RequestOptions` for per-call overrides:

```java
RequestOptions opts = RequestOptions.builder()
    .endUserId("eu_...")
    .idempotencyKey("order-8812-invoice")
    .timeout(Duration.ofSeconds(30))
    .maxRetries(0)
    .serverTimeout(Duration.ofSeconds(60))
    .build();
```

## End users

QuickBooks Desktop operations (`client.qbd()...`) run against one end user's company file and send `Daapi-End-User-Id`. Set a default on the builder, derive a client per end user, or pass it per call:

```java
DesktopAccountingApiClient acme = client.forEndUser("eu_01j9x4m6v4c8k2t7q0r5s3w1zb"); // shares the HTTP transport
acme.qbd().customers().retrieve("80000001-1700000000");
client.qbd().healthCheck(RequestOptions.endUser("eu_..."));
```

Without an end user, QuickBooks Desktop calls throw `DaapiException` before sending anything. Platform operations (`endUsers()`, `authSessions()`, `requests()`) never send the header.

## Models and types

- Response models are immutable with accessor methods (`invoice.refNumber()`). Unknown fields are kept in `additionalProperties()`; missing fields read as `null`. `toJson()` writes the wire JSON back.
- Money and other decimal fields are `BigDecimal` and travel as decimal strings with their scale (`new BigDecimal("5.00")` is sent as `"5.00"`). Quantities and rates without fixed precision are `Double`.
- Dates are `LocalDate`; timestamps are `OffsetDateTime` with the offset QuickBooks reported. Timestamps are always sent with seconds (`2026-10-05T09:14:00-07:00`).
- Filters such as `updatedAfter` accept a `String`, `LocalDate` or `OffsetDateTime`.
- Enums are open: fields are `String`, and the known values are constants (`DateMacro.THIS_MONTH`, `RequestStatus.SUCCEEDED`). Values added to the API later pass through unchanged.
- Input objects have fluent setters. Fields you never set are not sent. On clearable fields, `null` sends JSON `null` and clears the value in QuickBooks:

```java
client.qbd().invoices().update("7-1700000000",
    new InvoiceUpdateInput("1700000007")   // required: revisionNumber
        .memo(null));                       // {"revisionNumber":"1700000007","memo":null}
```

## Pagination

Cursor lists return a `Pager<T>`. Nothing is fetched until you use it.

```java
Pager<Customer> customers = client.qbd().customers().list(new CustomerListParams().limit(100));

for (Customer c : customers) { ... }                 // every item, page after page
customers.stream().filter(...).count();              // java.util.stream
List<Customer> all = customers.listAll();            // everything in memory
Page<Customer> first = customers.firstPage();        // one request
first.data(); first.nextCursor(); first.hasMore(); first.remainingCount(); first.cursorExpiresAt();
for (Page<Customer> page : customers.pages()) { ... }
```

When page N arrives, the SDK requests page N+1 in the background, so the next request reaches the API inside the cursor's idle window while you process page N. A network error on a continue request retries the same cursor.

A QuickBooks cursor expires when no continue request arrives within its idle window, or when the QuickBooks session ends. Iteration then throws `CursorExpiredException` (a subclass of `InvalidRequestException`). The SDK never restarts a list on its own, because records may have changed. Restart with a watermark:

```java
try {
    for (Customer c : client.qbd().customers().list(params)) process(c);
} catch (CursorExpiredException e) {
    System.out.println(e.itemsYielded() + " items, " + e.pagesServed() + " pages, last " + e.lastId() + ", reason " + e.reason());
    // Continue with records changed since the last one processed; skip IDs you already have.
    for (Customer c : client.qbd().customers().list(new CustomerListParams().limit(100).updatedAfter(e.lastUpdatedAt()))) process(c);
}
```

Lists without an iterator in QuickBooks (accounts, classes, terms and other small lists) return their list object directly, for example `AccountList` with `data()`.

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

`ApiException` exposes every field of the error body: `status()`, `type()`, `code()`, `getMessage()`, `userFacingMessage()`, `httpStatusCode()`, `integrationCode()`, `requestId()` (falls back to the `Daapi-Request-Id` header), `errorCause()` (the API's `cause`; `getCause()` is the Java exception chain), `fixes()` (`actor()`, `action()`), `docsUrl()`, `retryable()`, `outcome()`, `param()`, `details()` and `headers()`. Codes are constants in `models.ErrorCode`, types in `models.ErrorType`.

```java
try {
    client.qbd().customers().retrieve("80000099-1700000000");
} catch (IntegrationException e) {
    if (ErrorCode.QBD_OBJECT_NOT_FOUND.equals(e.code())) { ... }
    System.out.println(e.userFacingMessage() + " " + e.docsUrl() + " " + e.requestId());
    e.fixes().forEach(f -> System.out.println(f.actor() + ": " + f.action()));
} catch (IntegrationConnectionException e) {
    // The end user has to act (open QuickBooks, close a dialog). Show e.userFacingMessage().
}
```

## Retries and idempotency

- Retried: network errors before a response (connection failure, reset, timeout) for reads and writes, `429`, and `5xx` responses with `Daapi-Should-Retry: true`.
- Never retried: `Daapi-Should-Retry: false`, and errors whose `outcome` is `unknown` or `pending`.
- Backoff: 0.5 s, 1 s, 2 s ... up to 8 s with jitter. `Retry-After` (seconds or HTTP date, including `0`) is honored; a value above 60 s throws instead of waiting.
- Every write sends an `Idempotency-Key`: yours (`RequestOptions.idempotencyKey`) or a UUID generated once per call and reused on every retry of that call, so a retried write is applied once.

## Timeouts

There are two timeouts:

- `timeout` (client side, default 100 s) limits each HTTP attempt.
- `serverTimeout` (`Daapi-Timeout-Seconds`) is how long the API waits for QuickBooks before it answers. When it is longer than `timeout`, each attempt waits `serverTimeout + 10 s`.

When the API answers `504 QBD_REQUEST_TIMEOUT` (the request reached QuickBooks but has not finished), the SDK does not resubmit. It long-polls `GET /v1/requests/{id}?waitSeconds=...` until the call's time budget (`timeout`) is used, then returns the result, throws the request's typed error, or throws `RequestPendingException` with `requestId()`. The request keeps running; look it up later with `client.requests().retrieve(id)`.

## Async requests

Operations that support async mode are also available under `enqueue()`. They return a `RequestHandle<T>` as soon as the API queued the request (`Prefer: respond-async`, `202 Accepted`):

```java
RequestHandle<Invoice> handle = client.qbd().invoices().enqueue().create(input,
    RequestOptions.builder().queueTtl(Duration.ofHours(1)).build());
handle.id();                 // req_...
handle.request().status();   // queued
handle.status();             // GET /v1/requests/{id}, current Request
Invoice invoice = handle.await(Duration.ofMinutes(10));  // long-polls; throws the typed error on failure
Invoice now = handle.result();                           // one read; RequestPendingException if not finished
```

(`Object.wait()` is final in Java, so the long-poll method is `await`.) Completion is also delivered by webhook.

## Webhooks

Deliveries follow [Standard Webhooks](https://www.standardwebhooks.com/). Verify the raw body before trusting it:

```java
try {
    WebhookEvent event = Webhooks.verify(rawBody, requestHeaders, System.getenv("DAAPI_WEBHOOK_SECRET"));
    if (WebhookEventType.REQUEST_SUCCEEDED.equals(event.type())) {
        Request request = client.requests().retrieve((String) event.data().get("id"));
    }
} catch (WebhookVerificationException e) {
    // respond 400
}
```

`requestHeaders` is a `Map<String, ?>` with `String` or `List<String>` values; names are matched case-insensitively. The secret is accepted with or without the `whsec_` prefix. Signatures rotate by sending several `v1,...` values; any match passes. The timestamp must be within 5 minutes of your clock in either direction; `WebhookVerifier.builder().tolerance(...).clock(...)` changes both. `client.webhooks()` returns the default verifier, and `Webhooks.verifySignature(...)` checks only the signature.

## Raw responses

Non-list methods have a `...WithResponse` variant returning `ApiResponse<T>` with the parsed result, HTTP status and headers:

```java
ApiResponse<Customer> r = client.qbd().customers().retrieveWithResponse("80000001-1700000000");
r.data(); r.statusCode(); r.requestId(); r.headers().get("Daapi-Warnings");
```

## Passthrough

Send qbXML request elements directly, as JSON or as raw XML:

```java
PassthroughResponse json = client.endUsers().passthrough("eu_...",
    new PassthroughInput().put("CustomerQueryRq", Map.of("MaxReturned", 5)));
Object rs = json.get("CustomerQueryRs");

String xml = client.endUsers().passthroughXml("eu_...",
    "<QBXMLMsgsRq onError=\"stopOnError\"><CustomerQueryRq><MaxReturned>5</MaxReturned></CustomerQueryRq></QBXMLMsgsRq>");
```

## Versioning

The SDK follows semantic versioning. It is generated from the API contract; `.daapi-sdk.json` records the contract's SHA-256 (`1cc3058cecb5...`), and `SdkInfo.VERSION`, `SdkInfo.API_VERSION` and `SdkInfo.CONTRACT_SHA256` expose the same at runtime. Additive API changes (new fields, new enum values, new error codes) do not break existing code.

## Development

```sh
mise install
mise run check   # build with -Xlint:all -Werror for Java 11, unit tests, conformance suite, javadoc, examples, Central bundle dry run
```

Code under `src/main/java/com/desktopaccountingapi/quickbooksdesktop/{models,services}` and `DesktopAccountingApiClient.java` is generated; see [CONTRIBUTING.md](CONTRIBUTING.md).

---

QuickBooks is a registered trademark of Intuit Inc. Desktop Accounting API is an independent product and is not affiliated with, endorsed by, or approved by Intuit Inc.

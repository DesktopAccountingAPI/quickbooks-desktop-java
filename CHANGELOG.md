# Changelog

## Unreleased

## 0.7.0 (2026-10-12)

- New `client.qbd().batch()`: up to 25 typed QuickBooks Desktop operations in one Web Connector round trip. Each member is the member class of its operation (`new BatchCustomerCreateInput().body(...)`, which sets `operationId` itself) and each result is an instance of its operation's result class (`BatchCustomerCreateResult`, whose `data()` is a `Customer`), or `UnknownBatchResult` for an operation added later. One idempotency key covers the batch; a lost response throws `OutcomeUnknownException` with `details().get("members")` and is never retried.
- A batch whose response was lost and whose create members the API later found in QuickBooks by their `externalId` returns each such member with `data()` null and the record's QuickBooks ID in `recoveredObjectId()` (on every batch result, nullable), on `client.requests().retrieve()` and on an idempotent replay.
- New error code `QBD_REQUEST_NOT_PROCESSED` (`ErrorCode.QBD_REQUEST_NOT_PROCESSED`): `409`, not retryable. QuickBooks skipped a batch member because an earlier member failed under `stopOnError` (status 3231).
- **Breaking (types only):** the credit card request info of receive payments is the class `QbdRequest`. It was read as the request resource class `Request` by mistake, so its accessors returned null.
- Change feed subscriptions: `client.changeFeedSubscriptions().create()`, `list()`, `retrieve()`, `update()` and `delete()` (`/v1/change-feed-subscriptions`). A subscription polls every connected end user of the project for changed and deleted records of the listed resources and sends `data.changed` webhooks with their IDs. New webhook event type `WebhookEventType.DATA_CHANGED` (`data.changed`). New error code `CHANGE_FEED_SUBSCRIPTION_LIMIT_REACHED` (`422`, not retryable).

## 0.6.0 (2026-10-12)

- **Breaking:** the list parameters type of `qbd.classes.list` is renamed from `ClasseListParams` to `ClassListParams` (the generator dropped only the final "s" of "classes"). Rename the type in your code; nothing else changes. No other type name was affected.
- `client.webhookEndpoints().rotateSecret()` works: the API now honors the Idempotency-Key the client always sends (a retry returns the same new secret without rotating twice) and answers an unknown endpoint ID with `404 RESOURCE_MISSING`. It answered `400 INVALID_PARAMETER`.
- New error code `QBD_SALES_TAX_THROUGH_DATE_TOO_EARLY` (`ErrorCode.QBD_SALES_TAX_THROUGH_DATE_TOO_EARLY`): `422`, not retryable. QuickBooks refused a sales tax payment check and asked for a later sales tax 'through' date (status 3140); nothing changed. If the end user records the sales tax in QuickBooks instead, read and link that check rather than creating another. It answered `QBD_REFERENCE_NOT_FOUND` on `vendorId`.
- A sales representative initial that is already in use answers `409 QBD_DUPLICATE_NAME` with `param` `initial` (QuickBooks status 3180). It answered `422 QBD_REQUEST_ERROR`.
- `QBD_OBJECT_IN_USE` is `retryable` only when the record is open or locked in QuickBooks. A deletion or void refused because other records still use the object is `retryable: false`, so the client no longer treats it as worth repeating.
- Status 3390 (too many open QuickBooks list iterators) is `QBD_TRANSIENT_ERROR` with `details.reason` `max_iterators`. The API now stops the QuickBooks iterator of a cursor you stop reading (after about 10 seconds idle, or when a fourth cursor list starts for the same end user), so breaking out of a list loop early no longer uses up QuickBooks' iterator limit.
- Report validation: `missing_checks` requires `accountIds` or `accountFullNames`, the job detail reports require `entityIds` or `entityFullNames`, and `physical_inventory_worksheet` rejects `reportDateMacro`, each with `400 INVALID_PARAMETER` naming the parameter. Unit of measure set create rejects `isActive: false`, which QuickBooks ignores.
- New error code `QBD_TRANSIENT_ERROR` (`ErrorCode.QBD_TRANSIENT_ERROR`): `503`, retryable, outcome `not_applied`. QuickBooks was briefly busy (status 3390, or 3180 when a list was modified by another user) and changed nothing. The client retries it automatically with the same idempotency key, which now runs the write again instead of replaying the error. These answered `QBD_REQUEST_ERROR` (`422`, not retryable).

## 0.5.4 (2026-10-09)

- New error code `ROUTE_NOT_FOUND` (`ErrorCode.ROUTE_NOT_FOUND`): a `404` for a method and path the API has no endpoint for, such as the API root. These answered `RESOURCE_MISSING`, whose cause describes a missing ID.

## 0.5.3 (2026-10-09)

- Released in lockstep with the other Desktop Accounting API packages; no entries for this package.

## 0.5.2 (2026-10-09)

- Released in lockstep with the other Desktop Accounting API packages; no entries for this package.

## 0.5.1 (2026-10-09)

- `ApiResponse.requestId()` after a long-polled call is the ID of the request that produced the result (the 504's `details.requestId`), which `client.requests().retrieve()` finds. It was the last poll's ID, which answers `404`. The poll's own ID stays in `headers().get("Daapi-Request-Id")`.
- The README says that required input fields are checked before the request is sent, not by the compiler: a field you never set, such as `fiscalYear` on `ReportBudgetSummaryParams`, throws `DaapiException` and nothing is sent. The constructor that takes the required fields is the compile-time-checked way to build an input.
- `QBD_OBJECT_IN_USE` errors (QuickBooks status 3175 or 3176, a record open for editing or locked by another user) carry `details.diagnosis` with the new diagnosis cause `record_in_use`: close the edit window in QuickBooks, then retry. The cause's `details.lockedBy` names the user when QuickBooks does. The request's `diagnosis` has the same cause.

## 0.5.0 (2026-10-09)

- A `504 QBD_REQUEST_TIMEOUT` is long-polled only when its HTTP status is 504 and it names a `details.requestId`, the rule every SDK follows (reads with outcome `not_applicable` and writes alike). Such a 504 is never retried.
- **Breaking:** a model field named `value` is now `value()` (and `value(...)` on inputs) instead of `valueValue()`, for example `ReportCell.value()`, `CustomField.value()` and `BarcodeCreateInput.value(...)`. The protected `InputObject.value(String)` helper is renamed `stored(String)`.
- **Breaking (types):** Response prices, rates and percentages (for example `QbdInvoiceLine.rate`, `QbdSalesOrPurchaseDetail.price`, `ratePercent`) carry the same decimal pattern as their inputs and as amounts, so they are `BigDecimal` instead of `String`.
- The README documents resuming a list from a stored `nextCursor()` (`new InvoiceListParams().cursor(savedCursor)`), now covered by the cross-language conformance suite, and the webhook verifier's `java.time.Clock`.

## 0.4.0 (2026-10-08)

- Warning code `QBD_PERSONAL_DATA_WITHHELD`: employee responses carry one warning per `ssn()` that QuickBooks withheld because the integration may not read personal data (`ssn()` stays `null`; `Daapi-Warnings` counts the warnings).
- `personalDataAccess()` on an end user's integration connections: `allowed`, `denied` or `unknown`.
- A failed passthrough's error `details().get("requests")` lists the status code, severity and message of every qbXML message, so a message skipped by `stopOnError` is visible.

## 0.3.0 (2026-10-08)

- **Breaking:** `qbd().reports().budgetSummary(params)` now requires `fiscalYear` in `ReportBudgetSummaryParams` (a required field, like `reportType`; the two-argument constructor sets both). The API always rejected a budget report without it (`400 INVALID_PARAMETER`, `param: "fiscalYear"`), so no working call changes behavior; code that omitted it still compiles and now throws `DaapiException` before sending instead of the API error. Set it with `new ReportBudgetSummaryParams(reportType, 2026)` or `.fiscalYear(2026)`.
- `WebhookEventType.CONNECTION_COMPANY_FILE_REMARKED` (`connection.company_file_remarked`): the marker that identifies a connection's company file was created, written back after the file lost it (for example a restored backup) or adopted from the file; `data.reason` is `marker_created`, `marker_restored` or `marker_adopted`.
- `ApiException.getRequestId()`, the same value as `requestId()`, for code and tools that expect bean-style getters. `ApiException.toString()` is documented: it adds the HTTP status, the error code and the request ID to the message (`...IntegrationException: 404 QBD_OBJECT_NOT_FOUND The QuickBooks object does not exist. (req_...)`), as .NET's `ToString()` does; `getMessage()` stays the API message. Log the exception, not only `getMessage()`, to keep the request ID.
- After `504 QBD_REQUEST_TIMEOUT`, any failure while waiting for the request (a poll answered `429`, `5xx` or `404`, a network error or a timeout) throws `RequestPendingException` with `requestId()`, `timeoutError()` (the 504, also the cause), `pollError()` and `idempotencyKey()`. It never surfaces the poll's own retryable exception, which read as "safe to resend" and could duplicate a write. `RequestHandle.await` follows the same rule.
- Waiting for a pending request stays inside the call's deadline (`totalTimeout`, else `timeout`): each poll, retry and backoff is cut off at the deadline.
- `idempotencyKey()` on every exception thrown for a write (generated or yours), on `ApiResponse` and on `RequestHandle`.
- A request that succeeded in QuickBooks but whose answer the API could not map (`request.error`, for example `QBD_RESPONSE_UNREADABLE` with outcome `applied`) throws that typed exception instead of failing to parse a null result.
- Exceptions thrown by `RequestHandle.await` and `result()` carry the handle's `idempotencyKey()`.
- A poll answer that arrives after the deadline (the default transport times the headers, not the body) is not returned, even a settled one; the call throws `RequestPendingException` with that snapshot.
- The default transport bounds the whole exchange, headers and body, by the attempt timeout (`HttpClient.sendAsync` canceled at the timeout), so a trickling body no longer holds the calling thread past the deadline.

## 0.2.1 (2026-10-07)

Generated from API contract sha256 `b5774d24bc81`. Documentation only; no API surface change.

- `updatedAt` and `revisionNumber` descriptions say that QuickBooks changes them at most once per second: an incremental sync should overlap `updatedAfter` and deduplicate by `id` and `revisionNumber`.
- The fixes for status 3261 (`QBD_INSUFFICIENT_PERMISSION`) name the personal-data checkbox in QuickBooks and what to do when it is gray: send the end user a new setup link and choose "Enable payroll access".
- Item sites document what QuickBooks returns without Advanced Inventory: an empty list, and `404 QBD_OBJECT_NOT_FOUND` from retrieve, rather than an error.

## 0.2.0 (2026-10-07)

- `defaultHeader(...)` / `defaultHeaders(...)` on the client builder, and `totalTimeout(...)` on the builder and `RequestOptions`: a time budget for a whole call, including retries and the wait for a pending request.
- A base URL ending in `/v1` (Conductor's form) no longer produces `/v1/v1/...`.
- `Pager<T>` requests the next page only when the iteration needs it, so a loop that stops early sends no extra QuickBooks query. While iterating items, a page held for more than 2 seconds makes the SDK request the next page in the background; `listAll()` always reads ahead.
- README: "Porting from Conductor".

## 0.1.1 (2026-10-06)

Generated from API contract sha256 `1cc3058cecb5`, the same contract as 0.1.0. No API surface change.

- The README is rewritten: install with exact package coordinates, authentication, a quickstart, common workflows, errors, async requests and webhooks, versioning and support. Every code sample in it is compiled against the package before release, and the quickstart runs against a mock server.

## 0.1.0 (2026-10-06)

First release, generated from API contract 1.0.0 (sha256 `1cc3058cecb5`, 275 operations).

- `DesktopAccountingApiClient` with builder, `fromEnv()` and `forEndUser(...)`; local secret-key validation.
- Typed models for every request and response: `BigDecimal` money with preserved scale, `LocalDate`, `OffsetDateTime`, open enums as string constants, unknown fields kept in `additionalProperties()`, `toJson()` on every model.
- Input objects that distinguish unset fields from explicit `null`.
- Auto-pagination (`Pager<T>`: `Iterable`, `stream()`, `firstPage()`, `pages()`, `listAll()`) with one page of read-ahead and `CursorExpiredException` progress fields.
- Retries for network errors, 429 and retryable 5xx responses with backoff, `Retry-After` and one idempotency key per call.
- Long-polling after `504 QBD_REQUEST_TIMEOUT` and `RequestPendingException`.
- Async mode through `enqueue()` returning `RequestHandle<T>`.
- Typed exceptions per error type with every error field; `ErrorCode` and `ErrorType` constants.
- `...WithResponse(...)` methods returning status and headers.
- Passthrough as JSON or raw qbXML.
- Standard Webhooks verification (`Webhooks`, `WebhookVerifier`).
- No runtime dependencies; Java 11 or later.

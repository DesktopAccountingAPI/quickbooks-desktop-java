# Changelog

## Unreleased

- **Breaking:** `qbd().reports().budgetSummary(params)` now requires `fiscalYear` in `ReportBudgetSummaryParams` (a required field, like `reportType`; the two-argument constructor sets both). The API always rejected a budget report without it (`400 INVALID_PARAMETER`, `param: "fiscalYear"`), so no working call changes behavior; code that omitted it now fails before sending instead of with the API error. Set it with `new ReportBudgetSummaryParams(reportType, 2026)` or `.fiscalYear(2026)`.
- `WebhookEventType.CONNECTION_COMPANY_FILE_REMARKED` (`connection.company_file_remarked`): the marker that identifies a connection's company file was created, written back after the file lost it (for example a restored backup) or adopted from the file; `data.reason` is `marker_created`, `marker_restored` or `marker_adopted`.
- `ApiException.getRequestId()`, the same value as `requestId()`, for code and tools that expect bean-style getters. `ApiException.toString()` is documented: it adds the HTTP status, the error code and the request ID to the message (`...IntegrationException: 404 QBD_OBJECT_NOT_FOUND The QuickBooks object does not exist. (req_...)`), as .NET's `ToString()` does; `getMessage()` stays the API message. Log the exception, not only `getMessage()`, to keep the request ID.
- After `504 QBD_REQUEST_TIMEOUT`, any failure while waiting for the request (a poll answered `429`, `5xx` or `404`, a network error or a timeout) throws `RequestPendingException` with `requestId()`, `timeoutError()` (the 504, also the cause), `pollError()` and `idempotencyKey()`. It never surfaces the poll's own retryable exception, which read as "safe to resend" and could duplicate a write. `RequestHandle.await` follows the same rule.
- Waiting for a pending request stays inside the call's deadline (`totalTimeout`, else `timeout`): each poll, retry and backoff is cut off at the deadline.
- `idempotencyKey()` on every exception thrown for a write (generated or yours), on `ApiResponse` and on `RequestHandle`.
- A request that succeeded in QuickBooks but whose answer the API could not map (`request.error`, for example `QBD_RESPONSE_UNREADABLE` with outcome `applied`) throws that typed exception instead of failing to parse a null result.
- Exceptions thrown by `RequestHandle.await` and `result()` carry the handle's `idempotencyKey()`.
- A poll answer that arrives after the deadline (the default transport times the headers, not the body) is not returned, even a settled one; the call throws `RequestPendingException` with that snapshot.
- The default transport bounds the whole exchange, headers and body, by the attempt timeout (`HttpClient.sendAsync` canceled at the timeout), so a trickling body no longer holds the calling thread past the deadline.

## 0.2.0

- `defaultHeader(...)` / `defaultHeaders(...)` on the client builder, and `totalTimeout(...)` on the builder and `RequestOptions`: a time budget for a whole call, including retries and the wait for a pending request.
- A base URL ending in `/v1` (Conductor's form) no longer produces `/v1/v1/...`.
- `Pager<T>` requests the next page only when the iteration needs it, so a loop that stops early sends no extra QuickBooks query. While iterating items, a page held for more than 2 seconds makes the SDK request the next page in the background; `listAll()` always reads ahead.
- README: "Porting from Conductor".

## 0.1.0

First release, generated from API contract 1.0.0 (sha256 `68a0d76d6b51`, 275 operations).

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

# Changelog

## 0.2.0

- `defaultHeader(...)` / `defaultHeaders(...)` on the client builder, and `totalTimeout(...)` on the builder and `RequestOptions`: a time budget for a whole call, including retries and the wait for a pending request.
- A base URL ending in `/v1` (Conductor's form) no longer produces `/v1/v1/...`.
- `Pager<T>` requests the next page only when the iteration needs it, so a loop that stops early sends no extra QuickBooks query. While iterating items, a page held for more than 2 seconds makes the SDK request the next page in the background; `listAll()` always reads ahead.
- README: "Porting from Conductor".

## 0.1.0

First release, generated from API contract 1.0.0 (sha256 `b5774d24bc81`, 275 operations).

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

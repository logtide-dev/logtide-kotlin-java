# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.5] - 2026-06-11

### Changed

- A `Retry-After` header on retryable responses now overrides the computed backoff delay (`HttpStatusException.retryAfterMs`)

## [0.9.4] - 2026-06-11

### Added

- Per-request scope isolation in the Ktor plugin: the default interceptor now activates a per-request `Scope` (coroutine-safe) alongside the trace-id element — breadcrumbs/user/tags set inside a handler stay local to that request, and the scope carries the request's trace context
- `ScopeContext.asContextElement(scope)` for custom coroutine-based middleware

## [0.9.3] - 2026-06-11

### Added

- **Scope API**: per-request/per-task context — `Scope`, `ScopeContext`, `withScope { }`, coroutine-safe `withScopeSuspend { }`, `LogTideUser`, `Breadcrumb`. Scope state (tags, extras, user, breadcrumbs ring buffer, session, trace context) is merged into every captured entry; entry-level values win
- `LogEntry.spanId` and `LogEntry.sessionId`, serialized as top-level `span_id` / `session_id`
- Per-request scope isolation in the Spring interceptor and Jakarta filter: breadcrumbs/user/tags set inside a request stay local to it, and request logs inherit the request's trace context

### Fixed

- Global metadata no longer overrides entry-level metadata on key collision (entry wins, per the cross-SDK contract)

## [0.9.2] - 2026-06-11

### Added

- DSN support: `LogTideClientOptions.fromDsn("https://lp_key@host[/path]") { ... }` plus `Dsn.parse` and `DsnParseException`. A malformed DSN throws at init time
- `LogTideClientOptions.service`: configure the service once and call log methods with just the message — `client.info("user logged in")`, `client.error("boom", exc)`. The legacy `(service, message)` overloads keep working
- Every entry now carries `metadata.sdk = {"name": "logtide-kotlin", "version": ...}` (caller-provided `sdk` key wins)

## [0.9.1] - 2026-06-11

### Added

- **New module `logtide-logback`**: a Logback appender (`dev.logtide.sdk.logback.LogTideAppender`) so existing SLF4J/Logback logging flows to LogTide via XML config or an injected shared client. Maps levels, turns MDC and SLF4J 2.x key/value pairs into metadata, promotes the `trace_id` MDC key to the top-level trace id, and serializes throwables to the canonical structured exception format

- W3C Trace Context support: `TraceContext` object with `parseTraceparent`, `formatTraceparent`, `generateTraceId`, `generateSpanId`, `resolveTraceId`
- Spring, Ktor and Jakarta integrations now resolve the inbound trace context per the W3C spec: `traceparent` header first, legacy `X-Trace-ID` as deprecated fallback, otherwise a new W3C trace ID is generated
- `HttpStatusException` carrying the response status code

### Changed

- **Breaking:** exceptions passed to `error`/`critical` are now serialized under the canonical `metadata.exception` key (was `metadata.error`), matching the platform's structured exception contract so server-side error grouping and fingerprinting work. Frames are now built from `Throwable.stackTrace` (no string parsing), a null exception message falls back to the type name, and the raw stack trace is included once at the top level
- Auto-generated trace IDs are now 32-char lowercase-hex W3C IDs instead of UUIDs
- Client errors (4xx except 408/429) are no longer retried: the batch is dropped after the first attempt instead of burning the full retry budget
- The per-log "No trace ID provided" warning has been removed (debug-level only now)

## [0.9.0] - 2026-06-11

### Fixed

- Logs are now sent to `POST {apiUrl}/api/v1/ingest` instead of `POST {apiUrl}`. `apiUrl` is documented as the base URL of the instance, so with the documented configuration every batch was posted to the root path and ingestion failed. URLs that already include `/api/v1/ingest` keep working (the path is not duplicated)

## [0.8.4] - 2026-03-19

### Fixed

- `printVersion` Gradle task now correctly reads version from `gradle.properties` instead of the root project (which defaulted to `unspecified`), fixing CI publish pipeline version check

## [0.5.0] - 2026-03-19

### Added

- **Multi-module Project Structure**: Project split into separate modules
  - `logtide-core`: Core SDK functionality
  - `logtide-ktor`: Ktor integration
  - `logtide-spring`: Spring Boot integration
  - `logtide-jakarta`: Jakarta Servlet integration
- **Jakarta Servlet Middleware**: Servlet filter with automatic request/response logging
- **Spring Boot Middleware**: Spring Boot interceptor with automatic request/response logging
- **Maven Publishing Automation**: Automated publishing with automatic releases and conditional signing
- **Gradle Convention Plugin**: Shared configuration plugin across modules
- **GitHub Templates**: Bug report and feature request issue templates

### Changed

- **Renamed LogWard → LogTide**: All classes and packages have been renamed (**breaking change**)
- Flush mechanism refactored to use Kotlin coroutines
- Trace ID validation now accepts non-UUID identifiers
- Serializers moved to `dev.logtide.sdk.serializers` package
- Replaced `println` statements with `logger.info` calls in middleware components
- Build configuration migrated to `projectGroup` and `projectVersion` from `gradle.properties`
- Ktor plugin now uses `LogTideClientOptions` for configuration
- README updated with logo, badges, and improved structure

### Removed

- `autoTraceId` option removed from `LogTideClientOptions`
- `checkVersionTag` task removed from build configuration

### Fixed

- Trace ID propagation in coroutine child scopes
- `currentTraceId` now uses `currentCoroutineContext` for correct retrieval
- Kotlin JVM plugin properly applied in `build.gradle.kts`

## [0.4.0] - 2026-01-02

### Added

- **Comprehensive Test Suite**: Significantly expanded test coverage from ~30% to ~75-80%
  - `LogWardClientOptionsTest`: 23 tests for configuration validation
  - `ExceptionTest`: 16 tests for exception hierarchy
  - `LogWardClientHttpTest`: 21 tests for HTTP flush/retry/circuit breaker with MockWebServer
  - `TraceIdContextTest`: 24 tests for coroutine-safe trace ID propagation
  - `ModelsSerializationTest`: 22 tests for JSON serialization
- **Middleware Testing**: Full test coverage for all framework integrations
  - `LogWardPluginTest`: 19 tests for Ktor middleware
  - `LogWardInterceptorTest`: 18 tests for Spring Boot interceptor
  - `LogWardFilterTest`: 25 tests for Jakarta Servlet filter

### Fixed

- Test dependencies now properly configured for runtime (`slf4j-api`, `slf4j-simple`)
- Framework test dependencies added (`ktor-server-test-host`, `spring-test`, `jakarta.servlet-api`)

### Changed

- Total test count increased from ~24 to ~192
- All middleware components now have dedicated unit and integration tests

## [0.3.0] - 2025-12-22

### Added

- **Coroutine-safe Trace ID**: Full support for Kotlin coroutines with proper trace ID propagation
  - `TraceIdElement`: A `CopyableThreadContextElement` that propagates trace ID across thread switches
  - `withTraceIdSuspend(traceId, block)`: Execute suspend function with coroutine-safe trace ID
  - `withNewTraceIdSuspend(block)`: Generate new trace ID for suspend functions
  - `getTraceIdSuspend()`: Get current trace ID from coroutine context
  - `currentTraceId()`: Top-level suspend function to get trace ID anywhere
- **Automatic Ktor Coroutine Integration**: Trace ID is now automatically propagated in all Ktor route coroutines
  - Uses `ApplicationCallPipeline.Call` intercept to wrap requests with `TraceIdElement`
  - No manual wrapping needed - just use the LogTide client in your routes
  - Trace ID from `X-Trace-ID` header or auto-generated UUID

### Changed

- Trace ID ThreadLocal is now shared across the SDK for consistency
- Ktor plugin now always generates a trace ID (from header or new UUID)
- Response logging includes trace ID in metadata

### Fixed

- Trace ID no longer gets lost when coroutines switch threads
- Trace ID properly propagates to child coroutines (launch/async)
- Nested trace ID contexts now restore correctly

## [0.2.0] - 2025-11-24

### Added

- **Middleware Examples**: Complete examples for all middleware integrations
  - Ktor plugin example with automatic and manual logging (`examples/middleware/ktor/KtorExample.kt`)
  - Spring Boot interceptor example (`examples/middleware/spring-boot/SpringBootExample.kt`)
  - Jakarta Servlet filter example with Jetty (`examples/middleware/jakarta-servlet/JakartaServletExample.kt`)
- **Manual Client Access**: `LogTideClientKey` for accessing LogTide client in Ktor routes
  - Access client via `call.application.attributes[LogTideClientKey]`
  - Enables custom logging alongside automatic HTTP logging
- **Initialization Logging**: Info logs when middleware is initialized
  - Ktor Plugin shows configuration summary on startup
  - Spring Boot Interceptor displays service configuration
  - Jakarta Servlet Filter outputs initialization details
- **Enhanced Documentation**:
  - README updated with middleware integration section
  - Manual client access examples
  - Complete usage guides for all frameworks

### Changed

- Ktor plugin now stores client in application attributes for manual access
- Improved middleware documentation with practical examples

## [0.1.0] - 2025-11-22

### Added

- Initial release of LogTide Kotlin SDK
- Automatic batching with configurable size and interval
- Retry logic with exponential backoff
- Circuit breaker pattern for fault tolerance
- Max buffer size with drop policy
- Query API for searching and filtering logs
- Live tail with Server-Sent Events (SSE)
- Trace ID context for distributed tracing
- Global metadata support
- Structured error serialization
- Internal metrics tracking
- Logging methods: debug, info, warn, error, critical
- Thread-safe operations with ThreadLocal trace context
- Graceful shutdown with JVM shutdown hook
- Kotlin coroutines support for async operations
- Full Kotlin idioms: data classes, inline functions, DSL builder

[0.8.4]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.8.4
[0.5.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.5.0
[0.4.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.4.0
[0.3.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.3.0
[0.2.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.2.0
[0.1.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.1.0

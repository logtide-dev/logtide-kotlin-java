# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.3.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.3.0
[0.2.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.2.0
[0.1.0]: https://github.com/logtide-dev/logtide-sdk-kotlin/releases/tag/v0.1.0

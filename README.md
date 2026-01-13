<p align="center">
  <img src="https://raw.githubusercontent.com/logtide-dev/logtide/main/docs/images/logo.png" alt="LogTide Logo" width="400">
</p>

<h1 align="center">LogTide Kotlin SDK</h1>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.logtide-dev/logtide-sdk-kotlin"><img src="https://img.shields.io/maven-central/v/io.github.logtide-dev/logtide-sdk-kotlin?color=blue" alt="Maven Central"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9+-purple.svg" alt="Kotlin"></a>
  <a href="https://github.com/logtide-dev/logtide-sdk-kotlin/releases"><img src="https://img.shields.io/github/v/release/logtide-dev/logtide-sdk-kotlin" alt="Release"></a>
</p>

<p align="center">
  Official Kotlin SDK for <a href="https://logtide.dev">LogTide</a> with automatic batching, retry logic, circuit breaker, query API, live streaming, and middleware support.
</p>

---

## Features

- ✅ **Automatic batching** with configurable size and interval
- ✅ **Retry logic** with exponential backoff
- ✅ **Circuit breaker** pattern for fault tolerance
- ✅ **Max buffer size** with drop policy to prevent memory leaks
- ✅ **Query API** for searching and filtering logs
- ✅ **Live tail** with Server-Sent Events (SSE)
- ✅ **Trace ID context** for distributed tracing
- ✅ **Global metadata** added to all logs
- ✅ **Structured error serialization**
- ✅ **Internal metrics** (logs sent, errors, latency, etc.)
- ✅ **Spring Boot, Ktor middleware** for auto-logging HTTP requests
- ✅ **Full Kotlin coroutines support** with suspend functions

## Requirements

- JVM 11 or higher
- Kotlin 1.9+ (or Java 11+ for Java interop)
- Gradle or Maven

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.logtide-dev:logtide-sdk-kotlin:0.2.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.logtide-dev:logtide-sdk-kotlin:0.2.0'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.logtide-dev</groupId>
    <artifactId>logtide-sdk-kotlin</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Quick Start

```kotlin
import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.models.LogTideClientOptions

val client = LogTideClient(
    LogTideClientOptions(
        apiUrl = "http://localhost:8080",
        apiKey = "lp_your_api_key_here"
    )
)

// Send logs
client.info("api-gateway", "Server started", mapOf("port" to 3000))
client.error("database", "Connection failed", RuntimeException("Timeout"))

// Graceful shutdown (also automatic on JVM shutdown)
runBlocking {
    client.close()
}
```

---

## Configuration Options

### Basic Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `apiUrl` | `String` | **required** | Base URL of your LogTide instance |
| `apiKey` | `String` | **required** | Project API key (starts with `lp_`) |
| `batchSize` | `Int` | `100` | Number of logs to batch before sending |
| `flushInterval` | `Duration` | `5.seconds` | Interval to auto-flush logs |

### Advanced Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `maxBufferSize` | `Int` | `10000` | Max logs in buffer (prevents memory leak) |
| `maxRetries` | `Int` | `3` | Max retry attempts on failure |
| `retryDelay` | `Duration` | `1.seconds` | Initial retry delay (exponential backoff) |
| `circuitBreakerThreshold` | `Int` | `5` | Failures before opening circuit |
| `circuitBreakerReset` | `Duration` | `30.seconds` | Time before retrying after circuit opens |
| `enableMetrics` | `Boolean` | `true` | Track internal metrics |
| `debug` | `Boolean` | `false` | Enable debug logging to console |
| `globalMetadata` | `Map<String, Any>` | `emptyMap()` | Metadata added to all logs |
| `autoTraceId` | `Boolean` | `false` | Auto-generate trace IDs for logs |

### Example: Full Configuration

```kotlin
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

val client = LogTideClient(
    LogTideClientOptions(
        apiUrl = "http://localhost:8080",
        apiKey = "lp_your_api_key_here",
        
        // Batching
        batchSize = 100,
        flushInterval = 5.seconds,
        
        // Buffer management
        maxBufferSize = 10000,
        
        // Retry with exponential backoff (1s → 2s → 4s)
        maxRetries = 3,
        retryDelay = 1.seconds,
        
        // Circuit breaker
        circuitBreakerThreshold = 5,
        circuitBreakerReset = 30.seconds,
        
        // Metrics & debugging
        enableMetrics = true,
        debug = true,
        
        // Global context
        globalMetadata = mapOf(
            "env" to System.getenv("APP_ENV"),
            "version" to "1.0.0",
            "hostname" to System.getenv("HOSTNAME")
        ),
        
        // Auto trace IDs
        autoTraceId = false
    )
)
```

---

## Logging Methods

### Basic Logging

```kotlin
client.debug("service-name", "Debug message")
client.info("service-name", "Info message", mapOf("userId" to 123))
client.warn("service-name", "Warning message")
client.error("service-name", "Error message", mapOf("custom" to "data"))
client.critical("service-name", "Critical message")
```

### Error Logging with Auto-Serialization

The SDK automatically serializes `Throwable` objects:

```kotlin
try {
    throw RuntimeException("Database timeout")
} catch (e: Exception) {
    // Automatically serializes error with stack trace
    client.error("database", "Query failed", e)
}
```

Generated log metadata:
```json
{
  "error": {
    "name": "RuntimeException",
    "message": "Database timeout",
    "stack": "..."
  }
}
```

---

## Trace ID Context

Track requests across services with trace IDs.

### Manual Trace ID

```kotlin
client.setTraceId("request-123")

client.info("api", "Request received")
client.info("database", "Querying users")
client.info("api", "Response sent")

client.setTraceId(null) // Clear context
```

### Scoped Trace ID

```kotlin
client.withTraceId("request-456") {
    client.info("api", "Processing in context")
    client.warn("cache", "Cache miss")
}
// Trace ID automatically restored after block
```

### Auto-Generated Trace ID

```kotlin
client.withNewTraceId {
    client.info("worker", "Background job started")
    client.info("worker", "Job completed")
}
```

---

## Query API

Search and retrieve logs programmatically.

### Basic Query

```kotlin
import dev.logtide.sdk.models.QueryOptions
import dev.logtide.sdk.enums.LogLevel
import java.time.Instant
import java.time.temporal.ChronoUnit

val result = client.query(
    QueryOptions(
        service = "api-gateway",
        level = LogLevel.ERROR,
        from = Instant.now().minus(24, ChronoUnit.HOURS),
        to = Instant.now(),
        limit = 100,
        offset = 0
    )
)

println("Found ${result.total} logs")
result.logs.forEach { log ->
    println(log)
}
```

### Full-Text Search

```kotlin
val result = client.query(
    QueryOptions(
        q = "timeout",
        limit = 50
    )
)
```

### Get Logs by Trace ID

```kotlin
val logs = client.getByTraceId("trace-123")
println("Trace has ${logs.size} logs")
```

---

## Live Streaming (SSE)

Stream logs in real-time using Server-Sent Events.

```kotlin
val cleanup = client.stream(
    onLog = { log ->
        println("[${log.time}] ${log.level}: ${log.message}")
    },
    onError = { error ->
        println("Stream error: ${error.message}")
    },
    filters = mapOf(
        "service" to "api-gateway",
        "level" to "error"
    )
)

// Stop streaming when done
Thread.sleep(60000)
cleanup()
```

---

## Metrics

Track SDK performance and health.

```kotlin
val metrics = client.getMetrics()

println("Logs sent: ${metrics.logsSent}")
println("Logs dropped: ${metrics.logsDropped}")
println("Errors: ${metrics.errors}")
println("Retries: ${metrics.retries}")
println("Avg latency: ${metrics.avgLatencyMs}ms")
println("Circuit breaker trips: ${metrics.circuitBreakerTrips}")

// Get circuit breaker state
println(client.getCircuitBreakerState()) // CLOSED, OPEN, or HALF_OPEN

// Reset metrics
client.resetMetrics()
```

---

## Middleware Integration

LogTide provides ready-to-use middleware for popular frameworks.

### Ktor Plugin

Automatically log HTTP requests and responses in Ktor applications.

```kotlin
import dev.logtide.sdk.middleware.LogTidePlugin
import io.ktor.server.application.*

fun Application.module() {
    install(LogTidePlugin) {
        apiUrl = "http://localhost:8080"
        apiKey = "lp_your_api_key_here"
        serviceName = "ktor-app"

        // Optional configuration
        logErrors = true
        skipHealthCheck = true
        skipPaths = setOf("/metrics", "/internal")

        // Client options
        batchSize = 100
        flushInterval = kotlin.time.Duration.parse("5s")
        enableMetrics = true
        globalMetadata = mapOf("env" to "production")
        
        // Enable request/response logging
        logRequests = true // Log incoming requests, e.g., method, path, headers
        logResponses = true // Log outgoing responses stats, e.g., status code, duration
        
        // Customize metadata extraction from calls, e.g., add user ID, session info, etc.
        extractMetadataFromIncomingCall = { call, traceId ->
            mapOf(
                "method" to call.request.httpMethod.value,
                "path" to call.request.uri,
                "remoteHost" to call.request.local.remoteHost,
                "traceId" to traceId
            )
        }
        
        // Customize metadata extraction from responses, e.g., status code, time elapsed, etc.
        extractMetadataFromOutgoingContent = { call, traceId, duration ->
            val statusValue = call.response.status()?.value
            val metadata = mutableMapOf(
                "method" to call.request.httpMethod.value,
                "path" to call.request.uri,
                "status" to (statusValue ?: 0),
                "duration" to (duration ?: 0L),
                "traceId" to traceId
            )
            metadata
        }
        
        // Extract trace ID from incoming requests (if any)
        // useful for using in combination with the CallId plugin
        extractTraceIdFromCall = { call ->
            call.request.headers["X-Trace-ID"]
        }
        
        // Whether to use the default interceptor to propagate trace IDs in call context
        // if you plan to access the client manually in routes
        useDefaultInterceptor = true
    }
}
```

**See full example:** [examples/middleware/ktor/KtorExample.kt](examples/middleware/ktor/KtorExample.kt)

#### Accessing the Client Manually in Ktor

You can access the LogTide client directly in your routes for custom logging:

```kotlin
import dev.logtide.sdk.middleware.LogTideClientKey

routing {
    get("/api/custom") {
        // Get the client from application attributes
        val client = call.application.attributes[LogTideClientKey]

        // Log custom messages
        client.info(
            "my-service",
            "Custom business logic executed",
            mapOf("userId" to 123, "action" to "custom_operation")
        )

        call.respondText("OK")
    }
}
```

### Spring Boot Interceptor

Automatically log HTTP requests and responses in Spring Boot applications.

```kotlin
import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.middleware.LogTideInterceptor
import dev.logtide.sdk.models.LogTideClientOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class LogTideConfig : WebMvcConfigurer {

    @Bean
    fun logTideClient() = LogTideClient(
        LogTideClientOptions(
            apiUrl = "http://localhost:8080",
            apiKey = "lp_your_api_key_here"
        )
    )

    @Bean
    fun logTideInterceptor(client: LogTideClient) = LogTideInterceptor(
        client = client,
        serviceName = "spring-boot-app",
        logRequests = true,
        logResponses = true,
        skipHealthCheck = true
    )

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(logTideInterceptor(logTideClient()))
    }
}
```

**See full example:** [examples/middleware/spring-boot/SpringBootExample.kt](examples/middleware/spring-boot/SpringBootExample.kt)

### Jakarta Servlet Filter

Automatically log HTTP requests and responses in Jakarta Servlet applications (Tomcat, Jetty, etc.).

```kotlin
import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.middleware.LogTideFilter
import dev.logtide.sdk.models.LogTideClientOptions

// Create client
val client = LogTideClient(
    LogTideClientOptions(
        apiUrl = "http://localhost:8080",
        apiKey = "lp_your_api_key_here"
    )
)

// Create filter
val filter = LogTideFilter(
    client = client,
    serviceName = "servlet-app",
    logRequests = true,
    logResponses = true,
    skipHealthCheck = true
)

// Add to servlet context
servletContext.addFilter("logTide", filter)
```

**Or via web.xml:**
```xml
<filter>
    <filter-name>LogTideFilter</filter-name>
    <filter-class>dev.logtide.sdk.middleware.LogTideFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>LogTideFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

**See full example:** [examples/middleware/jakarta-servlet/JakartaServletExample.kt](examples/middleware/jakarta-servlet/JakartaServletExample.kt)

---

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `./gradlew check` to ensure tests pass
5. Submit a pull request

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [LogTide Website](https://logtide.dev)
- [Documentation](https://logtide.dev/docs/sdks/kotlin/)
- [GitHub Issues](https://github.com/logtide-dev/logtide-sdk-kotlin/issues)

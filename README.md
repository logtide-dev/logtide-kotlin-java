# LogWard Kotlin SDK

Official Kotlin SDK for LogWard with advanced features: automatic batching, retry logic, circuit breaker, query API, live streaming, and middleware support.

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
    implementation("io.github.logward-dev:logward-sdk-kotlin:0.2.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.logward-dev:logward-sdk-kotlin:0.2.0'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.logward-dev</groupId>
    <artifactId>logward-sdk-kotlin</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Quick Start

```kotlin
import dev.logward.sdk.LogWardClient
import dev.logward.sdk.models.LogWardClientOptions

val client = LogWardClient(
    LogWardClientOptions(
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
| `apiUrl` | `String` | **required** | Base URL of your LogWard instance |
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

val client = LogWardClient(
    LogWardClientOptions(
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
import dev.logward.sdk.models.QueryOptions
import dev.logward.sdk.enums.LogLevel
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

LogWard provides ready-to-use middleware for popular frameworks.

### Ktor Plugin

Automatically log HTTP requests and responses in Ktor applications.

```kotlin
import dev.logward.sdk.middleware.LogWardPlugin
import io.ktor.server.application.*

fun Application.module() {
    install(LogWardPlugin) {
        apiUrl = "http://localhost:8080"
        apiKey = "lp_your_api_key_here"
        serviceName = "ktor-app"

        // Optional configuration
        logRequests = true
        logResponses = true
        logErrors = true
        skipHealthCheck = true
        skipPaths = setOf("/metrics", "/internal")

        // Client options
        batchSize = 100
        flushInterval = kotlin.time.Duration.parse("5s")
        enableMetrics = true
        globalMetadata = mapOf("env" to "production")
    }
}
```

**See full example:** [examples/middleware/ktor/KtorExample.kt](examples/middleware/ktor/KtorExample.kt)

#### Accessing the Client Manually in Ktor

You can access the LogWard client directly in your routes for custom logging:

```kotlin
import dev.logward.sdk.middleware.LogWardClientKey

routing {
    get("/api/custom") {
        // Get the client from application attributes
        val client = call.application.attributes[LogWardClientKey]

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
import dev.logward.sdk.LogWardClient
import dev.logward.sdk.middleware.LogWardInterceptor
import dev.logward.sdk.models.LogWardClientOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class LogWardConfig : WebMvcConfigurer {

    @Bean
    fun logWardClient() = LogWardClient(
        LogWardClientOptions(
            apiUrl = "http://localhost:8080",
            apiKey = "lp_your_api_key_here"
        )
    )

    @Bean
    fun logWardInterceptor(client: LogWardClient) = LogWardInterceptor(
        client = client,
        serviceName = "spring-boot-app",
        logRequests = true,
        logResponses = true,
        skipHealthCheck = true
    )

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(logWardInterceptor(logWardClient()))
    }
}
```

**See full example:** [examples/middleware/spring-boot/SpringBootExample.kt](examples/middleware/spring-boot/SpringBootExample.kt)

### Jakarta Servlet Filter

Automatically log HTTP requests and responses in Jakarta Servlet applications (Tomcat, Jetty, etc.).

```kotlin
import dev.logward.sdk.LogWardClient
import dev.logward.sdk.middleware.LogWardFilter
import dev.logward.sdk.models.LogWardClientOptions

// Create client
val client = LogWardClient(
    LogWardClientOptions(
        apiUrl = "http://localhost:8080",
        apiKey = "lp_your_api_key_here"
    )
)

// Create filter
val filter = LogWardFilter(
    client = client,
    serviceName = "servlet-app",
    logRequests = true,
    logResponses = true,
    skipHealthCheck = true
)

// Add to servlet context
servletContext.addFilter("logWard", filter)
```

**Or via web.xml:**
```xml
<filter>
    <filter-name>LogWardFilter</filter-name>
    <filter-class>dev.logward.sdk.middleware.LogWardFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>LogWardFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

**See full example:** [examples/middleware/jakarta-servlet/JakartaServletExample.kt](examples/middleware/jakarta-servlet/JakartaServletExample.kt)

---

## License

MIT

---

## Contributing

Contributions are welcome! Please open an issue or PR on [GitHub](https://github.com/logward-dev/logward-sdk-kotlin).

---

## Support

- **Documentation**: [https://logward.dev/docs](https://logward.dev/docs)
- **Issues**: [GitHub Issues](https://github.com/logward-dev/logward-sdk-kotlin/issues)

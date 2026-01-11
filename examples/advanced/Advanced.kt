package dev.logtide.sdk.examples

import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.enums.LogLevel
import dev.logtide.sdk.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Advanced usage example for LogTide Kotlin SDK
 * Demonstrates all features: query API, streaming, metrics, trace context, etc.
 */
fun main() = runBlocking {
    // Initialize client with full configuration
    val client = LogTideClient(
        LogTideClientOptions(
            apiUrl = "http://localhost:8080",
            apiKey = "lp_your_api_key_here",
            batchSize = 50,
            flushInterval = 3.seconds,
            maxBufferSize = 5000,
            maxRetries = 5,
            retryDelay = 500.seconds,
            circuitBreakerThreshold = 3,
            circuitBreakerReset = 20.seconds,
            enableMetrics = true,
            debug = true,
            globalMetadata = mapOf(
                "env" to "production",
                "version" to "1.0.0",
                "region" to "eu-west-1"
            ),
            autoTraceId = false
        )
    )

    println("=== LogTide Advanced Features Demo ===\n")

    // 1. Trace ID Context
    println("1. Trace ID Context")
    client.withTraceId("demo-trace-123") {
        client.info("demo", "Starting transaction")
        client.info("database", "Executing query")
        client.info("cache", "Updating cache")
        client.info("demo", "Transaction completed")
    }
    println("✓ Sent 4 logs with trace ID: demo-trace-123\n")

    // 2. Auto-generated Trace ID
    println("2. Auto-generated Trace ID")
    client.withNewTraceId {
        val traceId = client.getTraceId()
        println("  Generated trace ID: $traceId")
        client.info("worker", "Background job started")
        client.info("worker", "Processing items")
        client.info("worker", "Job completed")
    }
    println()

    // 3. Error Serialization
    println("3. Error Serialization")
    try {
        throw RuntimeException("Simulated database error")
    } catch (e: Exception) {
        client.error("database", "Query failed", e)
        println("✓ Logged error with full stack trace\n")
    }

    // 4. Different Log Levels
    println("4. Different Log Levels")
    client.debug("api", "Debug information")
    client.info("api", "Request processed successfully")
    client.warn("cache", "Cache miss")
    client.error("auth", "Authentication failed")
    client.critical("system", "Critical system error")
    println("✓ Logged all severity levels\n")

    // 5. Flush logs manually
    println("5. Manual Flush")
    client.flush()
    println("✓ Flushed buffered logs\n")

    // Wait a bit for logs to be sent
    delay(1000)

    // 6. Query API
    println("6. Query API")
    try {
        val result = client.query(
            QueryOptions(
                level = LogLevel.ERROR,
                from = Instant.now().minus(1, ChronoUnit.HOURS),
                to = Instant.now(),
                limit = 10
            )
        )
        println("  Found ${result.total} error logs")
        result.logs.take(3).forEach { log ->
            println("  - [${log.level}] ${log.service}: ${log.message}")
        }
    } catch (e: Exception) {
        println("  ✗ Query failed (server not available): ${e.message}")
    }
    println()

    // 7. Get logs by trace ID
    println("7. Get Logs by Trace ID")
    try {
        val logs = client.getByTraceId("demo-trace-123")
        println("  Found ${logs.size} logs for trace ID 'demo-trace-123'")
    } catch (e: Exception) {
        println("  ✗ Query failed (server not available): ${e.message}")
    }
    println()

    // 8. Aggregated Statistics
    println("8. Aggregated Statistics")
    try {
        val stats = client.getAggregatedStats(
            AggregatedStatsOptions(
                from = Instant.now().minus(7, ChronoUnit.DAYS),
                to = Instant.now(),
                interval = "1h"
            )
        )
        println("  Time series buckets: ${stats.timeseries.size}")
        println("  Top services:")
        stats.topServices.take(3).forEach { service ->
            println("    - ${service.service}: ${service.count} logs")
        }
    } catch (e: Exception) {
        println("  ✗ Stats query failed (server not available): ${e.message}")
    }
    println()

    // 9. Live Streaming (comment this out if testing without server)
    /*
    println("9. Live Streaming (SSE)")
    val cleanup = client.stream(
        onLog = { log ->
            println("  [STREAM] [${log.level}] ${log.service}: ${log.message}")
        },
        onError = { error ->
            println("  [STREAM ERROR] ${error.message}")
        },
        filters = mapOf("level" to "error")
    )
    
    // Stream for 5 seconds
    delay(5000)
    cleanup()
    println("✓ Stopped streaming\n")
    */

    // 10. Metrics Tracking
    println("10. Metrics Tracking")
    val metrics = client.getMetrics()
    println("  Logs sent: ${metrics.logsSent}")
    println("  Logs dropped: ${metrics.logsDropped}")
    println("  Errors: ${metrics.errors}")
    println("  Retries: ${metrics.retries}")
    println("  Avg latency: ${metrics.avgLatencyMs}ms")
    println("  Circuit breaker trips: ${metrics.circuitBreakerTrips}")
    println("  Circuit state: ${client.getCircuitBreakerState()}")
    println()

    // 11. Reset Metrics
    println("11. Reset Metrics")
    client.resetMetrics()
    val resetMetrics = client.getMetrics()
    println("  Logs sent after reset: ${resetMetrics.logsSent}")
    println()

    // 12. Complex Metadata
    println("12. Complex Metadata")
    client.info(
        "api",
        "Complex request processed",
        mapOf(
            "user" to mapOf(
                "id" to 12345,
                "email" to "user@example.com",
                "roles" to listOf("admin", "user")
            ),
            "request" to mapOf(
                "method" to "POST",
                "path" to "/api/users",
                "headers" to mapOf(
                    "content-type" to "application/json"
                )
            ),
            "performance" to mapOf(
                "executionTime" to 45.3,
                "queriesCount" to 3
            )
        )
    )
    println("✓ Logged with nested metadata structures\n")

    // Cleanup
    println("=== Cleanup ===")
    client.close()
    println("✓ Client closed gracefully")
    println("\n✅ Demo completed!")
}

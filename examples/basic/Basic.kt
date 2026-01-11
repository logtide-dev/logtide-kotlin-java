package dev.logtide.sdk.examples

import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.models.LogTideClientOptions
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Basic usage example for LogTide Kotlin SDK
 */
fun main() = runBlocking {
    // Initialize client
    val client = LogTideClient(
        LogTideClientOptions(
            apiUrl = "http://localhost:8080",
            apiKey = "lp_your_api_key_here"
        )
    )

    // Send simple logs
    client.info("api-gateway", "Server started", mapOf("port" to 3000))
    client.warn("cache", "Cache miss", mapOf("key" to "user:123"))

    // Error with exception
    try {
        throw RuntimeException("Database connection timeout")
    } catch (e: Exception) {
        client.error("database", "Connection failed", e)
    }

    // With trace ID context
    client.withTraceId("request-123") {
        client.info("api", "Processing request")
        client.info("database", "Querying users")
        client.info("api", "Sending response")
    }

    // Manual flush and cleanup
    client.flush()
    println("Logs sent! Check your LogTide dashboard.")
    
    // Close client (also called automatically on JVM shutdown)
    client.close()
}

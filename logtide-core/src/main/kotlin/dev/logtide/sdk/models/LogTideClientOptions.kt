package dev.logtide.sdk.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration options for LogTide client
 */
data class LogTideClientOptions(
    var apiUrl: String,
    var apiKey: String,
    var batchSize: Int = 100,
    var flushInterval: Duration = 5.seconds,
    var maxBufferSize: Int = 10000,
    var maxRetries: Int = 3,
    var retryDelay: Duration = 1.seconds,
    var circuitBreakerThreshold: Int = 5,
    var circuitBreakerReset: Duration = 30.seconds,
    var enableMetrics: Boolean = true,
    var debug: Boolean = false,
    var globalMetadata: Map<String, Any> = emptyMap()
) {
    init {
        require(apiUrl.isNotBlank()) { "apiUrl cannot be blank" }
        require(apiKey.isNotBlank()) { "apiKey cannot be blank" }
        require(batchSize > 0) { "batchSize must be positive" }
        require(maxBufferSize > 0) { "maxBufferSize must be positive" }
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(circuitBreakerThreshold > 0) { "circuitBreakerThreshold must be positive" }
    }
    
    @Deprecated("Use flushInterval property with Duration", ReplaceWith("flushInterval.inWholeMilliseconds"))
    val flushIntervalMs: Long get() = flushInterval.inWholeMilliseconds
    
    @Deprecated("Use retryDelay property with Duration", ReplaceWith("retryDelay.inWholeMilliseconds"))
    val retryDelayMs: Long get() = retryDelay.inWholeMilliseconds
    
    @Deprecated("Use circuitBreakerReset property with Duration", ReplaceWith("circuitBreakerReset.inWholeMilliseconds"))
    val circuitBreakerResetMs: Long get() = circuitBreakerReset.inWholeMilliseconds
}

/**
 * DSL builder for LogTideClientOptions
 */
class LogTideClientOptionsBuilder {
    var apiUrl: String = ""
    var apiKey: String = ""
    var batchSize: Int = 100
    var flushInterval: Duration = 5.seconds
    var maxBufferSize: Int = 10000
    var maxRetries: Int = 3
    var retryDelay: Duration = 1.seconds
    var circuitBreakerThreshold: Int = 5
    var circuitBreakerReset: Duration = 30.seconds
    var enableMetrics: Boolean = true
    var debug: Boolean = false
    var globalMetadata: Map<String, Any> = emptyMap()
    
    fun build(): LogTideClientOptions = LogTideClientOptions(
        apiUrl = apiUrl,
        apiKey = apiKey,
        batchSize = batchSize,
        flushInterval = flushInterval,
        maxBufferSize = maxBufferSize,
        maxRetries = maxRetries,
        retryDelay = retryDelay,
        circuitBreakerThreshold = circuitBreakerThreshold,
        circuitBreakerReset = circuitBreakerReset,
        enableMetrics = enableMetrics,
        debug = debug,
        globalMetadata = globalMetadata
    )
}

/**
 * Create LogTideClient with DSL configuration
 */
fun logTideClient(block: LogTideClientOptionsBuilder.() -> Unit): LogTideClientOptions {
    return LogTideClientOptionsBuilder().apply(block).build()
}

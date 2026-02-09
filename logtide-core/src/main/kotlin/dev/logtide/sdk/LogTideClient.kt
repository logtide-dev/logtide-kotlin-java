@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@file:Suppress("unused") // Public API functions may not be used internally

package dev.logtide.sdk

import dev.logtide.sdk.enums.CircuitState
import dev.logtide.sdk.enums.LogLevel
import dev.logtide.sdk.exceptions.BufferFullException
import dev.logtide.sdk.exceptions.CircuitBreakerOpenException
import dev.logtide.sdk.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.pow

/**
 * LogTide Kotlin SDK Client
 *
 * Main client for sending logs to LogTide with automatic batching,
 * retry logic, circuit breaker, and query capabilities.
 */
class LogTideClient(private val options: LogTideClientOptions) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Thread-safe buffer for batching
    private val buffer: MutableList<LogEntry> = Collections.synchronizedList(mutableListOf())

    // Circuit breaker
    private val circuitBreaker = CircuitBreaker(
        threshold = options.circuitBreakerThreshold,
        resetMs = options.circuitBreakerReset.inWholeMilliseconds
    )

    // Metrics tracking
    private val metricsLock = Any()
    private var metrics = ClientMetrics()
    private val latencyWindow = mutableListOf<Double>()
    private val maxLatencyWindow = 100

    // Trace ID context (uses shared ThreadLocal from TraceIdContext for coroutine compatibility)
    internal val traceIdContext: ThreadLocal<String?> get() = threadLocalTraceId

    // Periodic flush timer
    private val flushExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        thread(start = false, name = "LogTide-Flush-Timer", isDaemon = true) { r.run() }
    }

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun baseRequestBuilder(url: String) = Request.Builder()
        .url(url)
        .header("X-API-Key", options.apiKey)

    init {
        // Setup periodic flush
        flushExecutor.scheduleAtFixedRate(
            { runBlocking { flush() } },
            options.flushInterval.inWholeMilliseconds,
            options.flushInterval.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )

        // Register shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            runBlocking {
                close()
            }
        })

        if (options.debug) {
            logger.debug("Client initialized with apiUrl={}", options.apiUrl)
        }
    }

    // ==================== Trace ID Context ====================

    /**
     * Set trace ID for subsequent logs
     * Automatically validates and normalizes to UUID v4
     */
    fun setTraceId(traceId: String?) {
        traceIdContext.set(normalizeTraceId(traceId))
    }

    /**
     * Get current trace ID
     */
    fun getTraceId(): String? = traceIdContext.get()

    /**
     * Execute function with a specific trace ID context
     */
    fun <T> withTraceId(traceId: String, block: () -> T): T {
        val previousTraceId = traceIdContext.get()
        try {
            traceIdContext.set(normalizeTraceId(traceId)!!)
            return block()
        } finally {
            traceIdContext.set(previousTraceId)
        }
    }

    /**
     * Execute function with a new auto-generated trace ID
     */
    fun <T> withNewTraceId(block: () -> T): T {
        return withTraceId(UUID.randomUUID().toString(), block)
    }

    // ==================== Coroutine-safe Trace ID Methods ====================

    /**
     * Execute suspend function with a specific trace ID context (coroutine-safe)
     *
     * This version properly propagates the trace ID across:
     * - Thread switches during suspension
     * - Child coroutines created with launch/async
     * - Context switches with withContext
     *
     * Example:
     * ```kotlin
     * client.withTraceIdSuspend("my-trace-id") {
     *     // All logs here will have the trace ID
     *     client.info("service", "Starting operation")
     *
     *     // Even in child coroutines
     *     coroutineScope {
     *         launch { client.info("service", "Child operation") }
     *     }
     * }
     * ```
     */
    suspend fun <T> withTraceIdSuspend(traceId: String, block: suspend () -> T): T {
        val normalizedTraceId = normalizeTraceId(traceId) ?: UUID.randomUUID().toString()
        return withContext(TraceIdElement(normalizedTraceId)) {
            block()
        }
    }

    /**
     * Execute suspend function with a new auto-generated trace ID (coroutine-safe)
     */
    suspend fun <T> withNewTraceIdSuspend(block: suspend () -> T): T {
        return withTraceIdSuspend(UUID.randomUUID().toString(), block)
    }

    /**
     * Get current trace ID (coroutine-safe)
     *
     * Checks both the coroutine context and ThreadLocal for compatibility
     * with both suspend and non-suspend code.
     */
    suspend fun getTraceIdSuspend(): String? {
        return currentCoroutineContext()[TraceIdElement]?.traceId ?: traceIdContext.get()
    }

    // ==================== Logging Methods ====================

    /**
     * Log a custom entry
     */
    fun log(entry: LogEntry) {
        var finalEntry = entry

        // Apply global metadata
        if (options.globalMetadata.isNotEmpty()) {
            val mergedMetadata = (entry.metadata ?: emptyMap()) + options.globalMetadata
            finalEntry = entry.copy(metadata = mergedMetadata)
        }

        // Apply trace ID context
        if (finalEntry.traceId == null) {
            val contextTraceId = traceIdContext.get()
            if (contextTraceId != null) {
                finalEntry = finalEntry.copy(traceId = contextTraceId)
            } else if (options.autoTraceId) {
                finalEntry = finalEntry.copy(traceId = UUID.randomUUID().toString())
            }
        }

        // Add to buffer
        if (buffer.size >= options.maxBufferSize) {
            if (options.enableMetrics) {
                synchronized(metricsLock) {
                    metrics = metrics.copy(logsDropped = metrics.logsDropped + 1)
                }
            }
            if (options.debug) {
                logger.debug("Buffer full, dropping log: ${entry.message}")
            }
            throw BufferFullException()
        }

        buffer.add(finalEntry)

        // Auto-flush if batch size reached
        if (buffer.size >= options.batchSize) {
            scope.launch { flush() }
        }
    }

    /**
     * Log debug message
     */
    fun debug(service: String, message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(service, LogLevel.DEBUG, message, metadata = metadata))
    }

    /**
     * Log info message
     */
    fun info(service: String, message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(service, LogLevel.INFO, message, metadata = metadata))
    }

    /**
     * Log warning message
     */
    fun warn(service: String, message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(service, LogLevel.WARN, message, metadata = metadata))
    }

    /**
     * Log error message
     * Can accept either metadata map or Throwable
     */
    fun error(service: String, message: String, metadataOrError: Any? = null) {
        val metadata = when (metadataOrError) {
            is Throwable -> mapOf("error" to serializeError(metadataOrError))
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") (metadataOrError as Map<String, Any>)
            null -> null
            else -> mapOf("data" to metadataOrError)
        }
        log(LogEntry(service, LogLevel.ERROR, message, metadata = metadata))
    }

    /**
     * Log critical message
     * Can accept either metadata map or Throwable
     */
    fun critical(service: String, message: String, metadataOrError: Any? = null) {
        val metadata = when (metadataOrError) {
            is Throwable -> mapOf("error" to serializeError(metadataOrError))
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") (metadataOrError as Map<String, Any>)
            null -> null
            else -> mapOf("data" to metadataOrError)
        }
        log(LogEntry(service, LogLevel.CRITICAL, message, metadata = metadata))
    }

    // ==================== Flush & Send ====================

    /**
     * Flush buffered logs to LogTide API
     * Implements retry logic with exponential backoff and circuit breaker pattern
     */
    suspend fun flush() {
        if (buffer.isEmpty()) return

        // Copy and clear buffer atomically
        val logsToSend = synchronized(buffer) {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer.clear()
            copy
        }

        if (options.debug) {
            logger.debug("Flushing ${logsToSend.size} logs...")
        }

        // Check circuit breaker
        if (!circuitBreaker.canAttempt()) {
            if (options.enableMetrics) {
                synchronized(metricsLock) {
                    metrics = metrics.copy(errors = metrics.errors + 1)
                }
            }
            if (options.debug) {
                logger.debug("Circuit breaker is OPEN, skipping flush")
            }
            throw CircuitBreakerOpenException()
        }

        // Retry logic with exponential backoff
        var attempt = 0
        var lastError: Exception? = null

        while (attempt <= options.maxRetries) {
            try {
                val startTime = System.currentTimeMillis()

                // Send logs via HTTP
                sendLogs(logsToSend)

                val latency = System.currentTimeMillis() - startTime

                // Success!
                circuitBreaker.recordSuccess()

                if (options.enableMetrics) {
                    synchronized(metricsLock) {
                        metrics = metrics.copy(
                            logsSent = metrics.logsSent + logsToSend.size,
                            retries = metrics.retries + attempt
                        )
                        updateLatency(latency.toDouble())
                    }
                }

                if (options.debug) {
                    logger.debug("Successfully sent ${logsToSend.size} logs in ${latency}ms")
                }

                return

            } catch (e: Exception) {
                lastError = e
                circuitBreaker.recordFailure()

                if (options.enableMetrics) {
                    synchronized(metricsLock) {
                        metrics = metrics.copy(errors = metrics.errors + 1)
                    }
                }

                if (attempt < options.maxRetries) {
                    val delayMs = options.retryDelay.inWholeMilliseconds * (2.0.pow(attempt.toDouble())).toLong()
                    if (options.debug) {
                        logger.error("Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${delayMs}ms...")
                    }
                    delay(delayMs)
                    attempt++
                } else {
                    break
                }
            }
        }

        // All retries failed
        if (options.debug) {
            logger.error("All retry attempts failed: ${lastError?.message}")
        }

        if (circuitBreaker.getState() == CircuitState.OPEN && options.enableMetrics) {
            synchronized(metricsLock) {
                metrics = metrics.copy(circuitBreakerTrips = metrics.circuitBreakerTrips + 1)
            }
        }
    }

    private suspend fun sendLogs(logs: List<LogEntry>) = withContext(Dispatchers.IO) {
        val payload = mapOf("logs" to logs)
        val jsonBody = json.encodeToString(payload)

        val request = baseRequestBuilder(options.apiUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
        }
    }

    // ==================== Query API ====================

    /**
     * Query logs with filters
     */
    suspend fun query(options: QueryOptions): LogsResponse = withContext(Dispatchers.IO) {
        val queryParams = buildQueryParams(options)
        val url = HttpUrl.Builder()
            .scheme(this@LogTideClient.options.apiUrl.substringBefore("://"))
            .host(this@LogTideClient.options.apiUrl.substringAfter("://").substringBefore("/"))
            .addPathSegments("api/v1/logs")
            .apply {
                queryParams.forEach { (key, value) ->
                    addQueryParameter(key, value)
                }
            }
            .build()

        val request = baseRequestBuilder(url.toString())
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Query failed: HTTP ${response.code}")
        }

        json.decodeFromString(response.body!!.string())
    }

    /**
     * Get logs by trace ID
     */
    suspend fun getByTraceId(traceId: String): List<LogEntry> {
        val response = query(QueryOptions(q = traceId))
        return response.logs.filter { it.traceId == traceId }
    }

    /**
     * Get aggregated statistics
     */
    suspend fun getAggregatedStats(options: AggregatedStatsOptions): AggregatedStatsResponse =
        withContext(Dispatchers.IO) {
            val queryParams = buildStatsParams(options)
            val url = HttpUrl.Builder()
                .scheme(this@LogTideClient.options.apiUrl.substringBefore("://"))
                .host(this@LogTideClient.options.apiUrl.substringAfter("://").substringBefore("/"))
                .addPathSegments("api/v1/logs/aggregated")
                .apply {
                    queryParams.forEach { (key, value) ->
                        addQueryParameter(key, value)
                    }
                }
                .build()

            val request = baseRequestBuilder(url.toString())
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Get stats failed: HTTP ${response.code}")
            }

            json.decodeFromString(response.body!!.string())
        }

    // ==================== Streaming ====================

    /**
     * Stream logs in real-time via Server-Sent Events
     * Returns a cleanup function to stop streaming
     */
    fun stream(
        onLog: (LogEntry) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        filters: Map<String, String> = emptyMap()
    ): () -> Unit {
        val url = HttpUrl.Builder()
            .scheme(options.apiUrl.substringBefore("://"))
            .host(options.apiUrl.substringAfter("://").substringBefore("/"))
            .addPathSegments("api/v1/logs/stream")
            .apply {
                filters.forEach { (key, value) ->
                    addQueryParameter(key, value)
                }
            }
            .build()

        val request = baseRequestBuilder(url.toString())
            .get()
            .build()

        val eventSource = EventSources.createFactory(httpClient)
            .newEventSource(request, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val log = json.decodeFromString<LogEntry>(data)
                        onLog(log)
                    } catch (e: Exception) {
                        onError?.invoke(e)
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    onError?.invoke(t ?: IOException("Stream failed"))
                }
            })

        return { eventSource.cancel() }
    }

    // ==================== Metrics ====================

    /**
     * Get SDK metrics
     */
    fun getMetrics(): ClientMetrics = synchronized(metricsLock) { metrics }

    /**
     * Reset SDK metrics
     */
    fun resetMetrics() {
        synchronized(metricsLock) {
            metrics = ClientMetrics()
            latencyWindow.clear()
        }
    }

    /**
     * Get circuit breaker state
     */
    fun getCircuitBreakerState(): CircuitState = circuitBreaker.getState()

    // ==================== Lifecycle ====================

    /**
     * Close client and flush remaining logs
     */
    suspend fun close() {
        if (options.debug) {
            logger.debug("Closing client...")
        }

        flush()
        flushExecutor.shutdown()
        withContext(Dispatchers.IO) {
            flushExecutor.awaitTermination(5, TimeUnit.SECONDS)
        }
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    // ==================== Helper Methods ====================

    internal fun normalizeTraceId(traceId: String?): String? {
        if (traceId == null) return null

        val uuidRegex =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$".toRegex(RegexOption.IGNORE_CASE)

        return if (uuidRegex.matches(traceId)) {
            traceId
        } else {
            if (options.debug) {
                logger.error("Invalid trace ID '$traceId', generating new UUID")
            }
            UUID.randomUUID().toString()
        }
    }

    private fun serializeError(error: Throwable): Map<String, Any?> {
        return mapOf(
            "name" to error::class.simpleName,
            "message" to error.message,
            "stack" to error.stackTraceToString()
        )
    }

    private fun updateLatency(latency: Double) {
        latencyWindow.add(latency)
        if (latencyWindow.size > maxLatencyWindow) {
            latencyWindow.removeAt(0)
        }
        metrics = metrics.copy(avgLatencyMs = latencyWindow.average())
    }

    private fun buildQueryParams(options: QueryOptions): Map<String, String> {
        val params = mutableMapOf<String, String>()
        options.service?.let { params["service"] = it }
        options.level?.let { params["level"] = it.value }
        options.from?.let { params["from"] = it.toString() }
        options.to?.let { params["to"] = it.toString() }
        options.q?.let { params["q"] = it }
        options.limit?.let { params["limit"] = it.toString() }
        options.offset?.let { params["offset"] = it.toString() }
        return params
    }

    private fun buildStatsParams(options: AggregatedStatsOptions): Map<String, String> {
        val params = mutableMapOf<String, String>()
        params["from"] = options.from.toString()
        params["to"] = options.to.toString()
        options.interval?.let { params["interval"] = it }
        options.service?.let { params["service"] = it }
        return params
    }
}

@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@file:Suppress("unused") // Public API functions may not be used internally

package dev.logtide.sdk

import dev.logtide.sdk.enums.CircuitState
import dev.logtide.sdk.enums.LogLevel
import dev.logtide.sdk.exceptions.BufferFullException
import dev.logtide.sdk.exceptions.CircuitBreakerOpenException
import dev.logtide.sdk.exceptions.HttpStatusException
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
    companion object {
        private const val MAX_TRACEID_LENGTH = 250
        private const val INGEST_PATH = "/api/v1/ingest"
        private const val MAX_STACK_FRAMES = 100
    }

    // apiUrl is the base URL of the instance; the ingest path is appended
    // here. URLs that already include it are accepted for backward
    // compatibility with configs that worked around the old behaviour.
    private val ingestUrl: String = options.apiUrl.trimEnd('/').let {
        if (it.endsWith(INGEST_PATH)) it else it + INGEST_PATH
    }

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

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun baseRequestBuilder(url: String) = Request.Builder()
        .url(url)
        .header("X-API-Key", options.apiKey)

    init {
        // Setup periodic flush
        scope.launch {
            while (isActive) {
                delay(options.flushInterval)
                flush()
            }
        }

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
        return withTraceId(TraceContext.generateTraceId(), block)
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
    suspend fun <T> withTraceIdSuspend(traceId: String, block: suspend CoroutineScope.() -> T): T {
        val normalizedTraceId =
            normalizeTraceId(traceId) ?: throw IllegalArgumentException("Invalid trace ID: $traceId")
        return withContext(TraceIdElement(normalizedTraceId)) {
            coroutineScope {
                block()
            }
        }
    }

    /**
     * Execute suspend function with a new auto-generated trace ID (coroutine-safe)
     */
    suspend fun <T> withNewTraceIdSuspend(block: suspend CoroutineScope.() -> T): T {
        return withTraceIdSuspend(TraceContext.generateTraceId(), block)
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

        // Active-span trace context (resolution order per spec 005 §4:
        // explicit -> active span -> scope -> client context/generation).
        if (finalEntry.traceId == null) {
            ActiveTraceContext.current()?.let { (activeTrace, activeSpan) ->
                finalEntry = finalEntry.copy(
                    traceId = activeTrace,
                    spanId = finalEntry.spanId ?: activeSpan,
                )
            }
        }

        // Merge the current scope (tags, user, breadcrumbs, session, trace
        // context). Runs before trace-id injection so the scope's trace
        // context wins over auto-generation (spec 005 §4).
        ScopeContext.threadLocalScope.get()?.let { scope ->
            finalEntry = scope.applyToEntry(finalEntry)
        }

        // Apply global metadata
        if (options.globalMetadata.isNotEmpty()) {
            // Global metadata is the base; entry-level values win (spec 004 §2)
            val mergedMetadata = options.globalMetadata + (finalEntry.metadata ?: emptyMap())
            finalEntry = finalEntry.copy(metadata = mergedMetadata)
        }

        // Stamp SDK identity (spec 003 §3); caller-provided value wins
        if (finalEntry.metadata?.containsKey("sdk") != true) {
            finalEntry = finalEntry.copy(
                metadata = (finalEntry.metadata ?: emptyMap()) +
                    mapOf("sdk" to mapOf("name" to SDK_NAME, "version" to SDK_VERSION))
            )
        }

        // beforeSend hook: may mutate or drop the entry. A buggy hook must
        // never lose the entry or raise to the caller.
        options.beforeSend?.let { hook ->
            val result = try {
                hook(finalEntry)
            } catch (hookError: Exception) {
                if (options.debug) {
                    logger.debug("beforeSend raised, keeping entry: ${hookError.message}")
                }
                finalEntry
            }
            finalEntry = result ?: return
        }

        // Sampling (applied after beforeSend, spec 005 §5)
        if (options.sampleRate < 1.0 && Math.random() > options.sampleRate) {
            return
        }

        // Apply trace ID context
        if (finalEntry.traceId == null) {
            val contextTraceId = traceIdContext.get()
            val metadataTraceId = entry.metadata?.get("traceId")?.toString()
            finalEntry = if (contextTraceId != null) {
                finalEntry.copy(traceId = contextTraceId)
            } else if (metadataTraceId != null) {
                finalEntry.copy(traceId = metadataTraceId)
            } else {
                if (options.debug) {
                    logger.debug("No trace ID provided for log, generating one automatically")
                }
                finalEntry.copy(traceId = TraceContext.generateTraceId())
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

    fun metadataOrErrorToMap(metadataOrError: Any?): Map<String, Any>? {
        return when (metadataOrError) {
            is Throwable -> mapOf("exception" to serializeError(metadataOrError))
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") (metadataOrError as Map<String, Any>)
            null -> null
            else -> mapOf("data" to metadataOrError)
        }
    }

    /**
     * Log debug message
     */
    fun debug(service: String, message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(service, LogLevel.DEBUG, message, metadata = metadata))
    }

    /** Log a debug message using the service configured in the options. */
    fun debug(message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(requireConfiguredService(), LogLevel.DEBUG, message, metadata = metadata))
    }

    /**
     * Log info message
     */
    fun info(service: String, message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(service, LogLevel.INFO, message, metadata = metadata))
    }

    /** Log an info message using the service configured in the options. */
    fun info(message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(requireConfiguredService(), LogLevel.INFO, message, metadata = metadata))
    }

    /**
     * Log warning message
     */
    fun warn(service: String, message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(service, LogLevel.WARN, message, metadata = metadata))
    }

    /** Log a warn message using the service configured in the options. */
    fun warn(message: String, metadata: Map<String, Any>? = null) {
        log(LogEntry(requireConfiguredService(), LogLevel.WARN, message, metadata = metadata))
    }

    /**
     * Log error message
     * Can accept either metadata map or Throwable
     */
    fun error(service: String, message: String, metadataOrError: Any? = null) {
        val metadata = metadataOrErrorToMap(metadataOrError)
        log(LogEntry(service, LogLevel.ERROR, message, metadata = metadata))
    }

    /** Log an error message using the service configured in the options. */
    fun error(message: String, metadataOrError: Any? = null) {
        val metadata = metadataOrErrorToMap(metadataOrError)
        log(LogEntry(requireConfiguredService(), LogLevel.ERROR, message, metadata = metadata))
    }

    /**
     * Log critical message
     * Can accept either metadata map or Throwable
     */
    fun critical(service: String, message: String, metadataOrError: Any? = null) {
        val metadata = metadataOrErrorToMap(metadataOrError)
        log(LogEntry(service, LogLevel.CRITICAL, message, metadata = metadata))
    }

    /** Log a critical message using the service configured in the options. */
    fun critical(message: String, metadataOrError: Any? = null) {
        val metadata = metadataOrErrorToMap(metadataOrError)
        log(LogEntry(requireConfiguredService(), LogLevel.CRITICAL, message, metadata = metadata))
    }

    private fun requireConfiguredService(): String =
        options.service ?: throw IllegalStateException(
            "No service configured: set LogTideClientOptions.service to call " +
                "log methods with just a message, or pass (service, message)"
        )

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

                // Permanent client errors (4xx except 408/429) will not become
                // valid by retrying: drop the batch after the first attempt.
                if (e is HttpStatusException && !e.isRetryable) {
                    if (options.debug) {
                        logger.error("Non-retryable error (HTTP ${e.statusCode}), dropping batch")
                    }
                    break
                }

                if (attempt < options.maxRetries) {
                    // A server-provided Retry-After overrides the computed backoff
                    val delayMs = (e as? HttpStatusException)?.retryAfterMs
                        ?: (options.retryDelay.inWholeMilliseconds * (2.0.pow(attempt.toDouble())).toLong())
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

        val request = baseRequestBuilder(ingestUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (options.debug) {
                    logger.error(response.body?.charStream()?.readText())
                    logger.error(payload.toString())
                }
                val retryAfterMs = response.header("Retry-After")
                    ?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong() }
                throw HttpStatusException(
                    response.code,
                    "Failed to send logs: HTTP ${response.code} - ${response.message}",
                    retryAfterMs,
                )
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
        runCatching {
            scope.cancel()
        }

        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    // ==================== Helper Methods ====================

    internal fun normalizeTraceId(traceId: String?): String? {
        if (traceId == null) return null

        return traceId.ifBlank {
            if (options.debug) {
                logger.error("Invalid trace ID '$traceId', generating a new one")
            }
            TraceContext.generateTraceId()
        }
    }

    /**
     * @see <a href="https://logtide.dev/docs/error-handling/">StructuredException Interface</a>
     */
    private fun serializeError(error: Throwable): Map<String, Any?> {
        // Canonical StructuredException shape (spec 003 §4): the backend's
        // error grouping reads metadata.exception with type/message/language,
        // stacktrace frames of {file, function, line}, and a nested cause chain.
        fun frames(current: Throwable): List<Map<String, Any?>> {
            return current.stackTrace.take(MAX_STACK_FRAMES).map { element ->
                mapOf(
                    "file" to (element.fileName ?: "<unknown>"),
                    "function" to "${element.className}.${element.methodName}",
                    "line" to element.lineNumber.takeIf { it >= 0 },
                )
            }
        }

        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())

        fun serializeRecursive(current: Throwable, isRoot: Boolean): Map<String, Any?> {
            visited.add(current)

            val type = current::class.simpleName ?: current.javaClass.name
            return buildMap {
                put("type", type)
                // The backend requires a non-empty message for grouping.
                put("message", current.message?.takeIf { it.isNotBlank() } ?: type)
                put("language", "kotlin")
                put("stacktrace", frames(current))
                if (isRoot) {
                    put("raw", current.stackTraceToString())
                }
                current.cause?.takeIf { visited.add(it) }?.let {
                    put("cause", serializeRecursive(it, isRoot = false))
                }
            }
        }

        return serializeRecursive(error, isRoot = true)
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

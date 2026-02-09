@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@file:Suppress("Unused")

package dev.logtide.sdk.ktor

import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.TraceIdElement
import dev.logtide.sdk.models.LogTideClientOptions
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.util.*

/**
 * AttributeKey to access the LogTide client instance
 *
 * Use this to manually log messages from your routes:
 * ```kotlin
 * val client = call.application.attributes[LogTideClientKey]
 * client.info("my-service", "Custom log message")
 * ```
 */
val LogTideClientKey = AttributeKey<LogTideClient>("LogTideClient")

/**
 * AttributeKey for trace ID propagation in coroutines
 */
private val TraceIdAttributeKey = AttributeKey<String>("LogTideTraceId")

/**
 * Ktor plugin for automatic HTTP request/response logging
 *
 * Example usage:
 * ```kotlin
 * fun Application.module() {
 *     install(LogTidePlugin) {
 *         apiUrl = "http://localhost:8080"
 *         apiKey = "lp_your_key"
 *         serviceName = "ktor-app"
 *     }
 *
 *     // Access the client manually in your routes
 *     routing {
 *         get("/api/custom") {
 *             val client = call.application.attributes[LogTideClientKey]
 *             client.info("my-service", "Custom log message")
 *             call.respondText("OK")
 *         }
 *     }
 * }
 * ```
 */
class LogTidePluginConfig {
    var apiUrl: String = ""
    var apiKey: String = ""
    var serviceName: String = "ktor-app"
    var logErrors: Boolean = true
    var skipHealthCheck: Boolean = true
    var skipPaths: Set<String> = emptySet()

    // Forward all LogTideClientOptions
    var batchSize: Int = 100
    var flushInterval: kotlin.time.Duration = kotlin.time.Duration.parse("5s")
    var maxBufferSize: Int = 10000
    var enableMetrics: Boolean = true
    var debug: Boolean = false
    var globalMetadata: Map<String, Any> = emptyMap()

    /**
     *   Whether to log incoming requests' metadata
     */
    var logRequests: Boolean = true

    /**
     *  Extract metadata from the outgoing response.
     *  By default, includes: method, path, remoteHost, and traceId.
     *
     *  @param call The incoming application call
     *  @param traceId The extracted or generated trace ID for the call
     *
     *  @return A map of metadata key-value pairs
     */
    var extractMetadataFromIncomingCall: (ApplicationCall, String) -> Map<String, Any> =
        { call, traceId ->
            mapOf(
                "method" to call.request.httpMethod.value,
                "path" to call.request.uri,
                "remoteHost" to call.request.local.remoteHost,
                "traceId" to traceId
            )
        }

    /**
     *  Whether to log outgoing responses' metadata
     */
    var logResponses: Boolean = true

    /**
     *  Extract metadata from the outgoing response.
     *  By default, includes: method, path, status, duration (time elapsed), and traceId.
     *
     *  @param call The outgoing application call
     *  @param traceId The extracted or generated trace ID for the call
     *  @param duration The duration in milliseconds taken to process the call
     *
     *  @return A map of metadata key-value pairs
     */
    var extractMetadataFromOutgoingContent: (ApplicationCall, String, Long?) -> Map<String, Any> =
        { call, traceId, duration ->
            val statusValue = call.response.status()?.value
            mapOf(
                "method" to call.request.httpMethod.value,
                "path" to call.request.uri,
                "status" to (statusValue ?: 0),
                "duration" to (duration ?: 0L),
                "traceId" to traceId
            )
        }

    /**
     *  Extracts a trace ID from the incoming call.
     *  By default, extracts the trace ID from the "X-Trace-ID" header if present.
     *
     *  @param call The incoming application call from which to extract the trace ID
     *  @return The extracted trace ID, or null to generate a new trace ID
     */
    var extractTraceIdFromCall: (ApplicationCall) -> String? = { call ->
        call.request.headers["X-Trace-ID"]
    }

    // Whether to use the default interceptor to propagate trace IDs in call context
    var useDefaultInterceptor: Boolean = true

    internal fun toClientOptions() = LogTideClientOptions(
        apiUrl = apiUrl,
        apiKey = apiKey,
        batchSize = batchSize,
        flushInterval = flushInterval,
        maxBufferSize = maxBufferSize,
        enableMetrics = enableMetrics,
        debug = debug,
        globalMetadata = globalMetadata
    )
}

val LogTidePlugin = createApplicationPlugin(
    name = "LogTide",
    createConfiguration = ::LogTidePluginConfig
) {
    val config = pluginConfig

    // Log plugin installation
    application.log.info("LogTide Plugin Initialized")
    application.log.info("  Service Name: ${config.serviceName}")
    application.log.info("  API URL: ${config.apiUrl}")
    application.log.info("  Batch Size: ${config.batchSize}")
    application.log.info("  Flush Interval: ${config.flushInterval}")
    application.log.info("  Log Requests: ${config.logRequests}")
    application.log.info("  Log Responses: ${config.logResponses}")
    application.log.info("  Log Errors: ${config.logErrors}")
    application.log.info("  Skip Health Check: ${config.skipHealthCheck}")
    if (config.skipPaths.isNotEmpty()) {
        application.log.info("  Skip Paths: ${config.skipPaths.joinToString(", ")}")
    }

    val client = LogTideClient(config.toClientOptions())
    application.log.info("  LogTide client created and ready")
    application.log.info("  Access client manually via: call.application.attributes[LogTideClientKey]")

    // Store client in application attributes for manual access
    application.attributes.put(LogTideClientKey, client)

    onCall { call ->
        val startTime = System.currentTimeMillis()
        val path = call.request.uri

        // Skip health checks and specified paths
        if (shouldSkip(path, config)) {
            return@onCall
        }

        // Extract or generate trace ID (always have one for coroutine propagation)
        val traceId = config.extractTraceIdFromCall(call)
            ?: UUID.randomUUID().toString()

        // Store trace ID for coroutine propagation
        call.attributes.put(TraceIdAttributeKey, traceId)

        // Set ThreadLocal for backwards compatibility with non-suspend code
        client.setTraceId(traceId)

        // Log request
        if (config.logRequests) {
            client.info(
                config.serviceName,
                "Request received",
                config.extractMetadataFromIncomingCall(call, traceId)
            )
        }

        // Store start time for response logging
        call.attributes.put(StartTimeKey, startTime)
    }

    if (config.useDefaultInterceptor) {
        // Intercept the call pipeline to propagate trace ID in coroutine context
        // This ensures all coroutines in the request handler have access to the trace ID
        application.intercept(ApplicationCallPipeline.Call) {
            val traceId = call.attributes.getOrNull(TraceIdAttributeKey)
            if (traceId != null) {
                // Wrap the entire call execution with TraceIdElement
                // This propagates the trace ID to all child coroutines
                withContext(TraceIdElement(traceId)) {
                    proceed()
                }
            } else {
                proceed()
            }
        }
    }

    val responseHandled = AttributeKey<Boolean>("RespondHandled")

    onCallRespond { call, body ->
        if (body !is OutgoingContent) return@onCallRespond
        if (call.attributes.getOrNull(responseHandled) == true) return@onCallRespond
        call.attributes.put(responseHandled, true)

        val path = call.request.uri

        if (shouldSkip(path, config)) {
            return@onCallRespond
        }

        val startTime = call.attributes.getOrNull(StartTimeKey)
        val duration = startTime?.let { System.currentTimeMillis() - it }
        val traceId = call.attributes.getOrNull(TraceIdAttributeKey)

        // Log response
        if (config.logResponses) {
            val effectiveTraceId = traceId ?: run {
                call.application.log.warn("Trace ID missing in response logging for path: $path, defaulting to 'unknown'. This may indicate a misconfiguration in the pipeline, such as disabling the default interceptor without supplying a new one in substitution.")
                "unknown"
            }

            val metadata = config.extractMetadataFromOutgoingContent(call, effectiveTraceId, duration)
            client.info(
                config.serviceName,
                "Response sent",
                metadata
            )
        }

        // Clear trace ID (ThreadLocal cleanup)
        client.setTraceId(null)
    }
}

private val StartTimeKey = AttributeKey<Long>("LogTideStartTime")

private fun shouldSkip(path: String, config: LogTidePluginConfig): Boolean {
    if (config.skipHealthCheck && path == "/health") {
        return true
    }
    return path in config.skipPaths
}

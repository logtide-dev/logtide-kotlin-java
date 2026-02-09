@file:Suppress("Unused")

package dev.logtide.sdk.spring

import dev.logtide.sdk.LogTideClient
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Spring Boot HandlerInterceptor for automatic HTTP request/response logging
 *
 * Example usage:
 * ```kotlin
 * @Configuration
 * class WebConfig : WebMvcConfigurer {
 *     @Bean
 *     fun logTideClient() = LogTideClient(...)
 *
 *     override fun addInterceptors(registry: InterceptorRegistry) {
 *         registry.addInterceptor(LogTideInterceptor(logTideClient(), "my-service"))
 *     }
 * }
 * ```
 */
class LogTideInterceptor(
    private val client: LogTideClient,
    private val serviceName: String,
    private val logRequests: Boolean = true,
    private val logResponses: Boolean = true,
    private val logErrors: Boolean = true,
    private val skipHealthCheck: Boolean = true,
    private val skipPaths: Set<String> = emptySet()
) : HandlerInterceptor {

    init {
        println("╭────────────────────────────────────────────╮")
        println("│  LogTide Interceptor Initialized          │")
        println("╰────────────────────────────────────────────╯")
        println("  Service Name: $serviceName")
        println("  Log Requests: $logRequests")
        println("  Log Responses: $logResponses")
        println("  Log Errors: $logErrors")
        println("  Skip Health Check: $skipHealthCheck")
        if (skipPaths.isNotEmpty()) {
            println("  Skip Paths: ${skipPaths.joinToString(", ")}")
        }
        println("✓ Spring Boot interceptor ready for HTTP logging")
        println()
    }

    companion object {
        private const val START_TIME_ATTR = "logtide.startTime"
        private const val TRACE_ID_HEADER = "X-Trace-ID"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val path = request.requestURI

        // Skip health checks and specified paths
        if (shouldSkip(path)) {
            return true
        }

        // Extract or generate trace ID
        val traceId = request.getHeader(TRACE_ID_HEADER)
        if (traceId != null) {
            client.setTraceId(traceId)
        }

        // Store start time
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis())

        // Log request
        if (logRequests) {
            client.info(
                serviceName,
                "Request started",
                mapOf(
                    "method" to request.method,
                    "path" to path,
                    "query" to (request.queryString ?: ""),
                    "remoteAddr" to request.remoteAddr
                )
            )
        }

        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val path = request.requestURI

        if (shouldSkip(path)) {
            return
        }

        val startTime = request.getAttribute(START_TIME_ATTR) as? Long
        val duration = startTime?.let { System.currentTimeMillis() - it }

        // Log error if exception occurred
        if (ex != null && logErrors) {
            client.error(
                serviceName,
                "Request failed",
                mapOf(
                    "method" to request.method,
                    "path" to path,
                    "status" to response.status,
                    "duration" to (duration ?: 0L) as Any,
                    "error" to mapOf(
                        "name" to ex::class.simpleName,
                        "message" to ex.message
                    )
                )
            )
        } else if (logResponses) {
            // Log successful response
            client.info(
                serviceName,
                "Request completed",
                mapOf(
                    "method" to request.method,
                    "path" to path,
                    "status" to response.status,
                    "duration" to (duration ?: 0L) as Any
                )
            )
        }

        // Clear trace ID context
        client.setTraceId(null)
    }

    private fun shouldSkip(path: String): Boolean {
        if (skipHealthCheck && (path == "/health" || path == "/actuator/health")) {
            return true
        }
        return path in skipPaths
    }
}
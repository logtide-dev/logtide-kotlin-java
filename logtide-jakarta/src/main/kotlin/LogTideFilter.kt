@file:Suppress("Unused")

package dev.logtide.sdk.jakarta

import dev.logtide.sdk.LogTideClient
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory

/**
 * Jakarta Servlet Filter for automatic HTTP request/response logging
 *
 * Compatible with any Jakarta Servlet-based framework (Tomcat, Jetty, etc.)
 *
 * Example usage (web.xml):
 * ```xml
 * <filter>
 *     <filter-name>LogTideFilter</filter-name>
 *     <filter-class>dev.logtide.sdk.middleware.LogTideFilter</filter-class>
 * </filter>
 * ```
 *
 * Or programmatically:
 * ```kotlin
 * servletContext.addFilter("logTide", LogTideFilter(client, "my-service"))
 * ```
 */
class LogTideFilter(
    private val client: LogTideClient,
    private val serviceName: String,
    private val logRequests: Boolean = true,
    private val logResponses: Boolean = true,
    private val logErrors: Boolean = true,
    private val skipHealthCheck: Boolean = true,
    private val skipPaths: Set<String> = emptySet()
) : Filter {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("LogTide Filter Initialized")
        logger.info("  Service Name: $serviceName")
        logger.info("  Log Requests: $logRequests")
        logger.info("  Log Responses: $logResponses")
        logger.info("  Log Errors: $logErrors")
        logger.info("  Skip Health Check: $skipHealthCheck")
        if (skipPaths.isNotEmpty()) {
            logger.info("  Skip Paths: ${skipPaths.joinToString(", ")}")
        }
        logger.info("  Jakarta Servlet filter ready for HTTP logging")
    }

    companion object {
        private const val START_TIME_ATTR = "logtide.startTime"
        private const val TRACE_ID_HEADER = "X-Trace-ID"
    }

    override fun init(filterConfig: FilterConfig?) {
        // No initialization needed
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        if (request !is HttpServletRequest || response !is HttpServletResponse) {
            chain.doFilter(request, response)
            return
        }

        val path = request.requestURI

        // Skip health checks and specified paths
        if (shouldSkip(path)) {
            chain.doFilter(request, response)
            return
        }

        // Extract or generate trace ID
        val traceId = request.getHeader(TRACE_ID_HEADER)
        if (traceId != null) {
            client.setTraceId(traceId)
        }

        val startTime = System.currentTimeMillis()
        request.setAttribute(START_TIME_ATTR, startTime)

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

        var exception: Throwable? = null

        try {
            // Continue filter chain
            chain.doFilter(request, response)
        } catch (e: Throwable) {
            exception = e
            throw e
        } finally {
            val duration = System.currentTimeMillis() - startTime

            // Log error if exception occurred
            if (exception != null && logErrors) {
                client.error(
                    serviceName,
                    "Request failed",
                    mapOf(
                        "method" to request.method,
                        "path" to path,
                        "status" to response.status,
                        "duration" to duration,
                        "error" to mapOf(
                            "name" to exception::class.simpleName,
                            "message" to exception.message
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
                        "duration" to duration
                    )
                )
            }

            // Clear trace ID context
            client.setTraceId(null)
        }
    }

    override fun destroy() {
        // No cleanup needed
    }

    private fun shouldSkip(path: String): Boolean {
        if (skipHealthCheck && (path == "/health" || path == "/actuator/health")) {
            return true
        }
        return path in skipPaths
    }
}
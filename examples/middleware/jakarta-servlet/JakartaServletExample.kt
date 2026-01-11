package dev.logtide.sdk.examples.middleware.servlet

import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.middleware.LogTideFilter
import dev.logtide.sdk.models.LogTideClientOptions
import jakarta.servlet.annotation.WebServlet
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Example of using LogTide with Jakarta Servlet (Jetty)
 *
 * This example demonstrates how to configure the LogTideFilter
 * for automatic HTTP request/response logging in a Jakarta Servlet application.
 */
fun main() {
    // Create LogTide client
    val logTideClient = LogTideClient(
        LogTideClientOptions(
            apiUrl = "http://localhost:8080",
            apiKey = "lp_your_api_key_here",
            batchSize = 100,
            flushInterval = 5.seconds,
            maxBufferSize = 10000,
            enableMetrics = true,
            debug = false,
            globalMetadata = mapOf(
                "env" to "production",
                "version" to "1.0.0",
                "service" to "servlet-app"
            )
        )
    )

    // Create LogTide filter
    val logTideFilter = LogTideFilter(
        client = logTideClient,
        serviceName = "servlet-app",
        logRequests = true,
        logResponses = true,
        logErrors = true,
        skipHealthCheck = true,
        skipPaths = setOf("/metrics", "/internal")
    )

    // Create Jetty server
    val server = Server(8080)
    val context = ServletContextHandler(ServletContextHandler.SESSIONS)
    context.contextPath = "/"
    server.handler = context

    // Add LogTide filter
    val filterHolder = context.addFilter(
        LogTideFilter::class.java,
        "/*",
        EnumSet.of(jakarta.servlet.DispatcherType.REQUEST)
    )
    filterHolder.filter = logTideFilter

    // Add example servlets
    context.addServlet(ServletHolder(HomeServlet()), "/")
    context.addServlet(ServletHolder(UsersServlet()), "/api/users")
    context.addServlet(ServletHolder(ErrorServlet()), "/api/error")
    context.addServlet(ServletHolder(HealthServlet()), "/health")

    // Start server
    println("Starting Jetty server on port 8080...")
    server.start()
    println("Server started! Visit http://localhost:8080")
    server.join()
}

@WebServlet(name = "HomeServlet", urlPatterns = ["/"])
class HomeServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/plain"
        resp.writer.write("Hello from Jakarta Servlet with LogTide!")
    }
}

@WebServlet(name = "UsersServlet", urlPatterns = ["/api/users"])
class UsersServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        // Simulate some work
        Thread.sleep(50)

        resp.contentType = "application/json"
        resp.writer.write("""
            {
                "users": [
                    {"id": 1, "name": "Alice"},
                    {"id": 2, "name": "Bob"}
                ]
            }
        """.trimIndent())
    }
}

@WebServlet(name = "ErrorServlet", urlPatterns = ["/api/error"])
class ErrorServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        // This will be logged as an error by LogTideFilter
        throw RuntimeException("Simulated error from Servlet")
    }
}

@WebServlet(name = "HealthServlet", urlPatterns = ["/health"])
class HealthServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        // This will be skipped by LogTide (skipHealthCheck = true)
        resp.contentType = "application/json"
        resp.writer.write("""{"status": "healthy"}""")
    }
}

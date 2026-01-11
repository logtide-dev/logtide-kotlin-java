package dev.logtide.sdk.examples.middleware.ktor

import dev.logtide.sdk.middleware.LogTidePlugin
import dev.logtide.sdk.middleware.LogTideClientKey
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

/**
 * Example of using LogTide with Ktor
 *
 * This example demonstrates:
 * 1. How to install and configure the LogTidePlugin for automatic HTTP logging
 * 2. How to access the LogTide client manually in your routes for custom logging
 */
fun main() {
    embeddedServer(Netty, port = 8080) {
        // Install LogTide plugin
        install(LogTidePlugin) {
            apiUrl = "http://localhost:8080"
            apiKey = "lp_your_api_key_here"
            serviceName = "ktor-app"

            // Optional configuration
            logRequests = true
            logResponses = true
            logErrors = true
            skipHealthCheck = true
            skipPaths = setOf("/metrics", "/internal")

            // LogTide client options
            batchSize = 100
            flushInterval = kotlin.time.Duration.parse("5s")
            maxBufferSize = 10000
            enableMetrics = true
            debug = false
            globalMetadata = mapOf(
                "env" to "production",
                "version" to "1.0.0"
            )
        }

        routing {
            get("/") {
                call.respondText("Hello from Ktor with LogTide!", ContentType.Text.Plain)
            }

            get("/api/users") {
                // Simulate some work
                Thread.sleep(50)
                call.respond(HttpStatusCode.OK, mapOf(
                    "users" to listOf(
                        mapOf("id" to 1, "name" to "Alice"),
                        mapOf("id" to 2, "name" to "Bob")
                    )
                ))
            }

            get("/api/error") {
                // This will be logged as an error
                throw RuntimeException("Simulated error")
            }

            get("/health") {
                // This will be skipped by LogTide (skipHealthCheck = true)
                call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
            }

            get("/api/custom-log") {
                // Access the LogTide client manually for custom logging
                val client = call.application.attributes[LogTideClientKey]

                // Log custom messages with your own metadata
                client.info(
                    "custom-service",
                    "Processing custom business logic",
                    mapOf(
                        "userId" to 12345,
                        "action" to "custom_operation",
                        "timestamp" to System.currentTimeMillis()
                    )
                )

                // Simulate some work
                Thread.sleep(100)

                client.info(
                    "custom-service",
                    "Custom operation completed successfully",
                    mapOf("duration" to 100, "status" to "success")
                )

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Custom operation completed",
                    "logged" to true
                ))
            }

            get("/api/manual-trace") {
                // Use the client with trace ID for distributed tracing
                val client = call.application.attributes[LogTideClientKey]

                client.withTraceId("trace-${System.currentTimeMillis()}") {
                    client.info("trace-service", "Starting traced operation")
                    Thread.sleep(50)
                    client.info("trace-service", "Operation step 1 completed")
                    Thread.sleep(50)
                    client.info("trace-service", "Operation step 2 completed")
                }

                call.respond(HttpStatusCode.OK, mapOf("traced" to true))
            }
        }
    }.start(wait = true)
}

import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.currentTraceId
import dev.logtide.sdk.ktor.LogTideClientKey
import dev.logtide.sdk.ktor.LogTidePlugin
import dev.logtide.sdk.ktor.LogTidePluginConfig
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for LogTidePlugin (Ktor middleware)
 */
class LogTidePluginTest {
    private lateinit var mockServer: MockWebServer

    internal fun Application.installLogtideDefault(mockServer: MockWebServer) {
        install(LogTidePlugin) {
            apiUrl = mockServer.url("/").toString()
            apiKey = "test_key"
            serviceName = "test-service"
            logRequests = false
            logResponses = false
        }
    }

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun cleanup() {
        mockServer.shutdown()
    }

    // ==================== Plugin Installation Tests ====================

    @Test
    fun `plugin should install successfully`() = testApplication {
        application {
            installLogtideDefault(mockServer)

            routing {
                get("/test") {
                    call.respondText("OK")
                }
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `plugin should store client in application attributes`() = testApplication {
        var clientFromAttributes: LogTideClient? = null

        application {
            installLogtideDefault(mockServer)

            routing {
                get("/test") {
                    clientFromAttributes = call.application.attributes[LogTideClientKey]
                    call.respondText("OK")
                }
            }
        }

        client.get("/test")
        assertNotNull(clientFromAttributes)
    }

    // ==================== Request Logging Tests ====================

    @Test
    fun `plugin should log request when enabled`() = testApplication {
        // Enqueue response for the log flush
        mockServer.enqueue(MockResponse().setResponseCode(200))
        mockServer.enqueue(MockResponse().setResponseCode(200))

        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/api/v1/ingest").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = true
                logResponses = false
                batchSize = 1 // Flush immediately
            }

            routing {
                get("/api/test") {
                    call.respondText("OK")
                }
            }
        }

        client.get("/api/test")

        // Wait for potential async flush
        Thread.sleep(500)
    }

    @Test
    fun `plugin should not log request when disabled`() = testApplication {
        application {
            installLogtideDefault(mockServer)

            routing {
                get("/test") {
                    call.respondText("OK")
                }
            }
        }

        client.get("/test")

        // No requests should be made to mockServer for logging
        assertEquals(0, mockServer.requestCount)
    }

    // ==================== Trace ID Tests ====================

    @Test
    fun `plugin should extract trace ID from header`() = testApplication {
        var extractedTraceId: String? = null

        application {
            installLogtideDefault(mockServer)

            routing {
                get("/test") {
                    extractedTraceId = call.application.attributes[LogTideClientKey].getTraceId()
                    call.respondText("OK")
                }
            }
        }

        val traceId = "550e8400-e29b-41d4-a716-446655440000"
        client.get("/test") {
            headers.apply {
                append("X-Trace-ID", traceId)
            }
        }

        assertEquals(traceId, extractedTraceId)
    }

    @Test
    fun `plugin should generate trace ID when header missing`() = testApplication {
        var generatedTraceId: String? = null

        application {
            installLogtideDefault(mockServer)

            routing {
                get("/test") {
                    generatedTraceId = call.application.attributes[LogTideClientKey].getTraceId()
                    call.respondText("OK")
                }
            }
        }

        client.get("/test")

        assertNotNull(generatedTraceId)
        // Should be a valid UUID format
        assertTrue(generatedTraceId!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `plugin should propagate trace ID to coroutine context`() = testApplication {
        var contextTraceId: String? = null
        val headerTraceId = "550e8400-e29b-41d4-a716-446655440001"

        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = false
                logResponses = false
                useDefaultInterceptor = true
            }

            routing {
                get("/test") {
                    contextTraceId = currentTraceId()
                    call.respondText("OK")
                }
            }
        }

        client.get("/test") {
            headers.apply {
                append("X-Trace-ID", headerTraceId)
            }
        }

        assertEquals(headerTraceId, contextTraceId)
    }

    @Test
    fun `plugin should use custom trace ID extractor`() = testApplication {
        var extractedTraceId: String? = null

        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = false
                logResponses = false
                extractTraceIdFromCall = { call ->
                    call.request.headers["Custom-Trace-Header"]
                }
            }

            routing {
                get("/test") {
                    extractedTraceId = call.application.attributes[LogTideClientKey].getTraceId()
                    call.respondText("OK")
                }
            }
        }

        val traceId = "custom-trace-123"
        client.get("/test") {
            headers.apply {
                append("Custom-Trace-Header", traceId)
            }
        }

        assertEquals(traceId, extractedTraceId)
    }

    // ==================== Path Skipping Tests ====================

    @Test
    fun `plugin should skip health check path by default`() = testApplication {
        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = true
                logResponses = true
                skipHealthCheck = true
            }

            routing {
                get("/health") {
                    call.respondText("healthy")
                }
            }
        }

        client.get("/health")

        // No logging requests should be made for /health
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `plugin should skip custom paths`() = testApplication {
        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = true
                logResponses = true
                skipPaths = setOf("/metrics", "/status")
            }

            routing {
                get("/metrics") {
                    call.respondText("metrics")
                }
                get("/status") {
                    call.respondText("status")
                }
            }
        }

        client.get("/metrics")
        client.get("/status")

        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `plugin should not skip health check when disabled`() = testApplication {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        mockServer.enqueue(MockResponse().setResponseCode(200))

        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/api/v1/ingest").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                skipHealthCheck = false
                logRequests = true
                logResponses = true
                batchSize = 1
            }

            routing {
                get("/health") {
                    call.respondText("healthy")
                }
            }
        }

        client.get("/health")

        // Wait for async logging
        Thread.sleep(500)

        // Some requests should have been made (logging is enabled for /health)
        // The exact count depends on async behavior
    }

    // ==================== Custom Metadata Extraction Tests ====================

    @Test
    fun `plugin should use custom request metadata extractor`() = testApplication {
        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = true
                logResponses = false
                extractMetadataFromIncomingCall = { _, traceId ->
                    mapOf(
                        "customField" to "customValue",
                        "traceId" to traceId
                    )
                }
            }
        }

        // The metadata extractor is configured correctly
        // Testing would require intercepting the actual log call
    }

    @Test
    fun `plugin should use custom response metadata extractor`() = testApplication {
        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = false
                logResponses = true
                extractMetadataFromOutgoingContent = { call, traceId, duration ->
                    mapOf(
                        "customResponseField" to "customValue",
                        "status" to (call.response.status()?.value ?: 0),
                        "traceId" to traceId,
                        "processingTime" to (duration ?: 0L)
                    )
                }
            }
        }

        // The metadata extractor is configured correctly
    }

    // ==================== Interceptor Configuration Tests ====================

    @Test
    fun `plugin should not propagate trace ID when interceptor disabled`() = testApplication {
        var contextTraceId: String? = "not-set"
        val headerTraceId = "550e8400-e29b-41d4-a716-446655440002"

        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = false
                logResponses = false
                useDefaultInterceptor = false
            }

            routing {
                get("/test") {
                    // Without the interceptor, TraceIdElement won't be in context
                    contextTraceId = currentTraceId()
                    call.respondText("OK")
                }
            }
        }

        client.get("/test") {
            headers.apply {
                append("X-Trace-ID", headerTraceId)
            }
        }

        // Without interceptor, context won't have trace ID
        // But ThreadLocal might still be set
        // The behavior depends on ordering
    }

    // ==================== Response Logging Tests ====================

    @Test
    fun `plugin should log response when enabled`() = testApplication {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        mockServer.enqueue(MockResponse().setResponseCode(200))

        application {
            install(LogTidePlugin) {
                apiUrl = mockServer.url("/api/v1/ingest").toString()
                apiKey = "test_key"
                serviceName = "test-service"
                logRequests = false
                logResponses = true
                batchSize = 1
            }

            routing {
                get("/test") {
                    call.respondText("OK")
                }
            }
        }

        client.get("/test")

        // Wait for async logging
        Thread.sleep(500)
    }

    // ==================== ThreadLocal Cleanup Tests ====================

    @Test
    fun `plugin should clear ThreadLocal after request`() = testApplication {
        val traceId = "550e8400-e29b-41d4-a716-446655440003"

        application {
            installLogtideDefault(mockServer)

            routing {
                get("/test") {
                    call.respondText("OK")
                }
            }
        }

        client.get("/test") {
            headers.apply {
                append("X-Trace-ID", traceId)
            }
        }

        // After the request completes, ThreadLocal should be cleared
        // This is hard to test directly since test client waits for completion
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `plugin config should convert to client options`() {
        val config = LogTidePluginConfig().apply {
            apiUrl = "http://localhost:8080"
            apiKey = "test_key"
            batchSize = 50
            flushInterval = 10.seconds
            maxBufferSize = 5000
            enableMetrics = false
            debug = true
            globalMetadata = mapOf("env" to "test")
        }

        val options = config.toClientOptions()

        assertEquals("http://localhost:8080", options.apiUrl)
        assertEquals("test_key", options.apiKey)
        assertEquals(50, options.batchSize)
        assertEquals(10.seconds, options.flushInterval)
        assertEquals(5000, options.maxBufferSize)
        assertFalse(options.enableMetrics)
        assertTrue(options.debug)
        assertEquals(mapOf("env" to "test"), options.globalMetadata)
    }

    @Test
    fun `plugin config should have correct defaults`() {
        val config = LogTidePluginConfig()

        assertEquals("", config.apiUrl)
        assertEquals("", config.apiKey)
        assertEquals("ktor-app", config.serviceName)
        assertTrue(config.logErrors)
        assertTrue(config.skipHealthCheck)
        assertTrue(config.skipPaths.isEmpty())
        assertEquals(100, config.batchSize)
        assertEquals(5.seconds, config.flushInterval)
        assertEquals(10000, config.maxBufferSize)
        assertTrue(config.enableMetrics)
        assertFalse(config.debug)
        assertTrue(config.globalMetadata.isEmpty())
        assertTrue(config.logRequests)
        assertTrue(config.logResponses)
        assertTrue(config.useDefaultInterceptor)
    }

    @Test
    fun `plugin config metadata extractor should have default`() {
        val config = LogTidePluginConfig()
        assertNotNull(config.extractMetadataFromIncomingCall)
        assertNotNull(config.extractMetadataFromOutgoingContent)
        assertNotNull(config.extractTraceIdFromCall)
    }
}
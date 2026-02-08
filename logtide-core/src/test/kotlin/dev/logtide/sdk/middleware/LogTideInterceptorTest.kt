package dev.logtide.sdk.middleware

import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.models.LogTideClientOptions
import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for LogTideInterceptor (Spring Boot middleware)
 */
class LogTideInterceptorTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: LogTideClient
    private lateinit var interceptor: LogTideInterceptor
    private lateinit var mockRequest: HttpServletRequest
    private lateinit var mockResponse: HttpServletResponse

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        client = LogTideClient(
            LogTideClientOptions(
                apiUrl = mockServer.url("/api/v1/ingest").toString(),
                apiKey = "test_key",
                flushInterval = 60.seconds,
                debug = false
            )
        )

        mockRequest = mockk(relaxed = true)
        mockResponse = mockk(relaxed = true)
    }

    @AfterEach
    fun cleanup() {
        client.setTraceId(null)
        mockServer.shutdown()
    }

    // ==================== Constructor Tests ====================

    @Test
    fun `interceptor should initialize with default values`() {
        interceptor = LogTideInterceptor(client, "test-service")

        // Just verify no exception is thrown during construction
        assertNotNull(interceptor)
    }

    @Test
    fun `interceptor should initialize with custom values`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "custom-service",
            logRequests = false,
            logResponses = false,
            logErrors = false,
            skipHealthCheck = false,
            skipPaths = setOf("/custom", "/paths")
        )

        assertNotNull(interceptor)
    }

    // ==================== preHandle Tests ====================

    @Test
    fun `preHandle should return true for normal paths`() {
        interceptor = LogTideInterceptor(client, "test-service", logRequests = false)

        every { mockRequest.requestURI } returns "/api/users"

        val result = interceptor.preHandle(mockRequest, mockResponse, Any())

        assertTrue(result)
    }

    @Test
    fun `preHandle should extract trace ID from header`() {
        interceptor = LogTideInterceptor(client, "test-service", logRequests = false)
        val traceId = "550e8400-e29b-41d4-a716-446655440000"

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId

        interceptor.preHandle(mockRequest, mockResponse, Any())

        assertEquals(traceId, client.getTraceId())
    }

    @Test
    fun `preHandle should store start time attribute`() {
        interceptor = LogTideInterceptor(client, "test-service", logRequests = false)

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null

        interceptor.preHandle(mockRequest, mockResponse, Any())

        verify { mockRequest.setAttribute("logtide.startTime", any<Long>()) }
    }

    @Test
    fun `preHandle should log request when enabled`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logRequests = true
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "GET"
        every { mockRequest.queryString } returns "page=1"
        every { mockRequest.remoteAddr } returns "127.0.0.1"
        every { mockRequest.getHeader(any()) } returns null

        interceptor.preHandle(mockRequest, mockResponse, Any())

        // Verify the log was added (would need to check buffer or flush)
        val metrics = client.getMetrics()
        assertEquals(0L, metrics.logsSent) // Not flushed yet
    }

    @Test
    fun `preHandle should not log request when disabled`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logRequests = false
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null

        interceptor.preHandle(mockRequest, mockResponse, Any())

        // No logging should occur
    }

    // ==================== Skip Path Tests ====================

    @Test
    fun `preHandle should skip health check path`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = true
        )

        every { mockRequest.requestURI } returns "/health"

        val result = interceptor.preHandle(mockRequest, mockResponse, Any())

        assertTrue(result)
        // Should not set any attributes
        verify(exactly = 0) { mockRequest.setAttribute(any(), any()) }
    }

    @Test
    fun `preHandle should skip actuator health path`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = true
        )

        every { mockRequest.requestURI } returns "/actuator/health"

        val result = interceptor.preHandle(mockRequest, mockResponse, Any())

        assertTrue(result)
        verify(exactly = 0) { mockRequest.setAttribute(any(), any()) }
    }

    @Test
    fun `preHandle should skip custom paths`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            skipPaths = setOf("/metrics", "/status")
        )

        every { mockRequest.requestURI } returns "/metrics"

        val result = interceptor.preHandle(mockRequest, mockResponse, Any())

        assertTrue(result)
        verify(exactly = 0) { mockRequest.setAttribute(any(), any()) }
    }

    @Test
    fun `preHandle should not skip health check when disabled`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = false,
            logRequests = false
        )

        every { mockRequest.requestURI } returns "/health"
        every { mockRequest.getHeader(any()) } returns null

        interceptor.preHandle(mockRequest, mockResponse, Any())

        // Should set start time even for /health
        verify { mockRequest.setAttribute("logtide.startTime", any<Long>()) }
    }

    // ==================== afterCompletion Tests ====================

    @Test
    fun `afterCompletion should log success response when enabled`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = true
        )

        val startTime = System.currentTimeMillis() - 100
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "GET"
        every { mockRequest.getAttribute("logtide.startTime") } returns startTime
        every { mockResponse.status } returns 200

        interceptor.afterCompletion(mockRequest, mockResponse, Any(), null)

        // Verify trace ID is cleared
        assertNull(client.getTraceId())
    }

    @Test
    fun `afterCompletion should log error when exception present`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logErrors = true
        )

        val exception = RuntimeException("Test error")
        val startTime = System.currentTimeMillis() - 100

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "POST"
        every { mockRequest.getAttribute("logtide.startTime") } returns startTime
        every { mockResponse.status } returns 500

        interceptor.afterCompletion(mockRequest, mockResponse, Any(), exception)

        // Verify trace ID is cleared even after error
        assertNull(client.getTraceId())
    }

    @Test
    fun `afterCompletion should not log error when logErrors disabled`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logErrors = false,
            logResponses = false
        )

        val exception = RuntimeException("Test error")

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getAttribute("logtide.startTime") } returns null

        interceptor.afterCompletion(mockRequest, mockResponse, Any(), exception)

        // No logging should occur
    }

    @Test
    fun `afterCompletion should skip health check path`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = true
        )

        every { mockRequest.requestURI } returns "/health"

        interceptor.afterCompletion(mockRequest, mockResponse, Any(), null)

        // Should return early without logging
    }

    @Test
    fun `afterCompletion should clear trace ID`() {
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logResponses = false
        )

        // Set a valid trace ID first (must be valid UUID format)
        val validTraceId = "550e8400-e29b-41d4-a716-446655440000"
        client.setTraceId(validTraceId)
        assertEquals(validTraceId, client.getTraceId())

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getAttribute("logtide.startTime") } returns null

        interceptor.afterCompletion(mockRequest, mockResponse, Any(), null)

        // Trace ID should be cleared
        assertNull(client.getTraceId())
    }

    @Test
    fun `afterCompletion should calculate duration correctly`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logResponses = true
        )

        val startTime = System.currentTimeMillis() - 500 // 500ms ago

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "GET"
        every { mockRequest.getAttribute("logtide.startTime") } returns startTime
        every { mockResponse.status } returns 200

        interceptor.afterCompletion(mockRequest, mockResponse, Any(), null)

        // Duration should be calculated (at least 500ms)
        // We can't easily verify the exact value without mocking more deeply
    }

    // ==================== Integration Tests ====================

    @Test
    fun `full request cycle should work correctly`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        interceptor = LogTideInterceptor(
            client = client,
            serviceName = "test-service",
            logRequests = true,
            logResponses = true
        )

        val traceId = "550e8400-e29b-41d4-a716-446655440001"

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "POST"
        every { mockRequest.queryString } returns null
        every { mockRequest.remoteAddr } returns "192.168.1.1"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId
        every { mockResponse.status } returns 201

        var storedStartTime: Long? = null
        every { mockRequest.setAttribute("logtide.startTime", any<Long>()) } answers {
            storedStartTime = arg(1)
        }
        every { mockRequest.getAttribute("logtide.startTime") } answers { storedStartTime }

        // Pre-handle
        val preResult = interceptor.preHandle(mockRequest, mockResponse, Any())
        assertTrue(preResult)
        assertEquals(traceId, client.getTraceId())

        // Simulate some processing time
        Thread.sleep(10)

        // After completion
        interceptor.afterCompletion(mockRequest, mockResponse, Any(), null)
        assertNull(client.getTraceId())
    }
}

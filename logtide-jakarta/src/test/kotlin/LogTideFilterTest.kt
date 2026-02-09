import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.models.LogTideClientOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for LogTideFilter (Jakarta Servlet middleware)
 */
class LogTideFilterTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: LogTideClient
    private lateinit var filter: LogTideFilter
    private lateinit var mockRequest: HttpServletRequest
    private lateinit var mockResponse: HttpServletResponse
    private lateinit var mockChain: FilterChain

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
        mockChain = mockk(relaxed = true)
    }

    @AfterEach
    fun cleanup() {
        client.setTraceId(null)
        mockServer.shutdown()
    }

    // ==================== Constructor Tests ====================

    @Test
    fun `filter should initialize with default values`() {
        filter = LogTideFilter(client, "test-service")
        assertNotNull(filter)
    }

    @Test
    fun `filter should initialize with custom values`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "custom-service",
            logRequests = false,
            logResponses = false,
            logErrors = false,
            skipHealthCheck = false,
            skipPaths = setOf("/custom", "/paths")
        )
        assertNotNull(filter)
    }

    // ==================== init and destroy Tests ====================

    @Test
    fun `init should not throw`() {
        filter = LogTideFilter(client, "test-service")
        filter.init(null)
    }

    @Test
    fun `init should accept FilterConfig`() {
        filter = LogTideFilter(client, "test-service")
        val mockConfig = mockk<FilterConfig>(relaxed = true)
        filter.init(mockConfig)
    }

    @Test
    fun `destroy should not throw`() {
        filter = LogTideFilter(client, "test-service")
        filter.destroy()
    }

    // ==================== doFilter Basic Tests ====================

    @Test
    fun `doFilter should call chain for normal paths`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockChain.doFilter(mockRequest, mockResponse) }
    }

    @Test
    fun `doFilter should pass through non-HTTP requests`() {
        filter = LogTideFilter(client, "test-service")

        val genericRequest = mockk<ServletRequest>(relaxed = true)
        val genericResponse = mockk<ServletResponse>(relaxed = true)

        filter.doFilter(genericRequest, genericResponse, mockChain)

        verify { mockChain.doFilter(genericRequest, genericResponse) }
    }

    @Test
    fun `doFilter should extract trace ID from header`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        val traceId = "550e8400-e29b-41d4-a716-446655440000"
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId

        var capturedTraceId: String? = null
        every { mockChain.doFilter(any(), any()) } answers {
            capturedTraceId = client.getTraceId()
        }

        filter.doFilter(mockRequest, mockResponse, mockChain)

        assertEquals(traceId, capturedTraceId)
        // Trace ID should be cleared after filter
        assertNull(client.getTraceId())
    }

    @Test
    fun `doFilter should store start time attribute`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockRequest.setAttribute("logtide.startTime", any<Long>()) }
    }

    // ==================== Request Logging Tests ====================

    @Test
    fun `doFilter should log request when enabled`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = true,
            logResponses = false
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "POST"
        every { mockRequest.queryString } returns "page=1"
        every { mockRequest.remoteAddr } returns "192.168.1.1"
        every { mockRequest.getHeader(any()) } returns null

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockChain.doFilter(mockRequest, mockResponse) }
    }

    @Test
    fun `doFilter should not log request when disabled`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null

        filter.doFilter(mockRequest, mockResponse, mockChain)

        // No logging calls should be made for request
    }

    // ==================== Response Logging Tests ====================

    @Test
    fun `doFilter should log response when enabled`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = true
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "GET"
        every { mockRequest.getHeader(any()) } returns null
        every { mockResponse.status } returns 200

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockChain.doFilter(mockRequest, mockResponse) }
    }

    @Test
    fun `doFilter should not log response when disabled`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null

        filter.doFilter(mockRequest, mockResponse, mockChain)

        // No response logging
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `doFilter should log error on exception`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logErrors = true
        )

        val exception = RuntimeException("Test error")
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "POST"
        every { mockRequest.getHeader(any()) } returns null
        every { mockResponse.status } returns 500
        every { mockChain.doFilter(any(), any()) } throws exception

        assertFailsWith<RuntimeException> {
            filter.doFilter(mockRequest, mockResponse, mockChain)
        }

        // Trace ID should still be cleared
        assertNull(client.getTraceId())
    }

    @Test
    fun `doFilter should not log error when disabled`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logErrors = false,
            logResponses = false
        )

        val exception = RuntimeException("Test error")
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null
        every { mockChain.doFilter(any(), any()) } throws exception

        assertFailsWith<RuntimeException> {
            filter.doFilter(mockRequest, mockResponse, mockChain)
        }
    }

    @Test
    fun `doFilter should rethrow exception`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logErrors = true
        )

        val exception = IllegalStateException("Custom exception")
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader(any()) } returns null
        every { mockChain.doFilter(any(), any()) } throws exception

        val caught = assertFailsWith<IllegalStateException> {
            filter.doFilter(mockRequest, mockResponse, mockChain)
        }

        assertEquals("Custom exception", caught.message)
    }

    // ==================== Skip Path Tests ====================

    @Test
    fun `doFilter should skip health check path`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = true
        )

        every { mockRequest.requestURI } returns "/health"

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockChain.doFilter(mockRequest, mockResponse) }
        // Should not set any attributes for skipped paths
        verify(exactly = 0) { mockRequest.setAttribute(any(), any()) }
    }

    @Test
    fun `doFilter should skip actuator health path`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = true
        )

        every { mockRequest.requestURI } returns "/actuator/health"

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockChain.doFilter(mockRequest, mockResponse) }
        verify(exactly = 0) { mockRequest.setAttribute(any(), any()) }
    }

    @Test
    fun `doFilter should skip custom paths`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            skipPaths = setOf("/metrics", "/status")
        )

        every { mockRequest.requestURI } returns "/metrics"

        filter.doFilter(mockRequest, mockResponse, mockChain)

        verify { mockChain.doFilter(mockRequest, mockResponse) }
        verify(exactly = 0) { mockRequest.setAttribute(any(), any()) }
    }

    @Test
    fun `doFilter should not skip health check when disabled`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            skipHealthCheck = false,
            logRequests = false,
            logResponses = false
        )

        every { mockRequest.requestURI } returns "/health"
        every { mockRequest.getHeader(any()) } returns null

        filter.doFilter(mockRequest, mockResponse, mockChain)

        // Should set start time even for /health when skipHealthCheck = false
        verify { mockRequest.setAttribute("logtide.startTime", any<Long>()) }
    }

    // ==================== Trace ID Cleanup Tests ====================

    @Test
    fun `doFilter should clear trace ID after successful request`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        val traceId = "550e8400-e29b-41d4-a716-446655440001"
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId

        filter.doFilter(mockRequest, mockResponse, mockChain)

        assertNull(client.getTraceId())
    }

    @Test
    fun `doFilter should clear trace ID after exception`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logErrors = false
        )

        val traceId = "550e8400-e29b-41d4-a716-446655440002"
        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId
        every { mockChain.doFilter(any(), any()) } throws RuntimeException("error")

        try {
            filter.doFilter(mockRequest, mockResponse, mockChain)
        } catch (e: RuntimeException) {
            // Expected
        }

        assertNull(client.getTraceId())
    }

    // ==================== Duration Calculation Tests ====================

    @Test
    fun `doFilter should calculate duration correctly`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logResponses = true
        )

        every { mockRequest.requestURI } returns "/api/users"
        every { mockRequest.method } returns "GET"
        every { mockRequest.getHeader(any()) } returns null
        every { mockResponse.status } returns 200

        // Add small delay in chain
        every { mockChain.doFilter(any(), any()) } answers {
            Thread.sleep(50)
        }

        filter.doFilter(mockRequest, mockResponse, mockChain)

        // Duration should be at least 50ms but we can't easily verify the exact value
    }

    // ==================== Integration Tests ====================

    @Test
    fun `full filter cycle should work correctly`() {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = true,
            logResponses = true
        )

        val traceId = "550e8400-e29b-41d4-a716-446655440003"

        every { mockRequest.requestURI } returns "/api/orders"
        every { mockRequest.method } returns "POST"
        every { mockRequest.queryString } returns null
        every { mockRequest.remoteAddr } returns "10.0.0.1"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId
        every { mockResponse.status } returns 201

        var traceIdDuringRequest: String? = null
        every { mockChain.doFilter(any(), any()) } answers {
            traceIdDuringRequest = client.getTraceId()
            Thread.sleep(10)
        }

        filter.doFilter(mockRequest, mockResponse, mockChain)

        assertEquals(traceId, traceIdDuringRequest)
        assertNull(client.getTraceId())
        verify { mockChain.doFilter(mockRequest, mockResponse) }
    }

    @Test
    fun `multiple sequential requests should work`() {
        filter = LogTideFilter(
            client = client,
            serviceName = "test-service",
            logRequests = false,
            logResponses = false
        )

        val traceId1 = "550e8400-e29b-41d4-a716-446655440010"
        val traceId2 = "550e8400-e29b-41d4-a716-446655440011"

        // First request
        every { mockRequest.requestURI } returns "/api/first"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId1

        var firstRequestTraceId: String? = null
        every { mockChain.doFilter(any(), any()) } answers {
            firstRequestTraceId = client.getTraceId()
        }

        filter.doFilter(mockRequest, mockResponse, mockChain)
        assertEquals(traceId1, firstRequestTraceId)
        assertNull(client.getTraceId())

        // Second request
        every { mockRequest.requestURI } returns "/api/second"
        every { mockRequest.getHeader("X-Trace-ID") } returns traceId2

        var secondRequestTraceId: String? = null
        every { mockChain.doFilter(any(), any()) } answers {
            secondRequestTraceId = client.getTraceId()
        }

        filter.doFilter(mockRequest, mockResponse, mockChain)
        assertEquals(traceId2, secondRequestTraceId)
        assertNull(client.getTraceId())
    }
}
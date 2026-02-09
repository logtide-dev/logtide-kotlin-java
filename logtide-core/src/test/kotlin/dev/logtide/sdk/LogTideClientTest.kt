package dev.logtide.sdk

import dev.logtide.sdk.enums.LogLevel
import dev.logtide.sdk.exceptions.BufferFullException
import dev.logtide.sdk.models.LogEntry
import dev.logtide.sdk.models.LogTideClientOptions
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for LogTideClient
 */
class LogTideClientTest {

    private lateinit var client: LogTideClient

    @BeforeEach
    fun setup() {
        client = LogTideClient(
            LogTideClientOptions(
                apiUrl = "http://localhost:8080",
                apiKey = "test_key",
                batchSize = 10,
                flushInterval = 10.seconds,
                maxBufferSize = 100,
                enableMetrics = true,
                debug = false
            )
        )
    }

    @AfterEach
    fun teardown() {
        // Clear shared ThreadLocal to avoid test pollution
        client.setTraceId(null)

        runBlocking {
            try {
                client.close()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
        }
    }

    @Test
    fun `should create log entry with correct level`() {
        client.info("test-service", "Test message")
        val metrics = client.getMetrics()
        // Buffer should have 1 log (not sent yet)
        assertTrue(metrics.logsSent == 0L)
    }

    @Test
    fun `should apply global metadata to logs`() {
        val clientWithMetadata = LogTideClient(
            LogTideClientOptions(
                apiUrl = "http://localhost:8080",
                apiKey = "test_key",
                globalMetadata = mapOf("env" to "test", "version" to "1.0.0")
            )
        )

        // This would apply global metadata
        clientWithMetadata.info("test-service", "Test message", mapOf("custom" to "value"))

        runBlocking {
            try {
                clientWithMetadata.close()
            } catch (e: Exception) {
                // Expected - no real server
            }
        }
    }

    @Test
    fun `should handle trace ID context`() {
        assertNull(client.getTraceId())

        val validTraceId = "550e8400-e29b-41d4-a716-446655440000"
        client.setTraceId(validTraceId)
        assertEquals(validTraceId, client.getTraceId())

        client.setTraceId(null)
        assertNull(client.getTraceId())
    }

    @Test
    fun `should handle scoped trace ID`() {
        assertNull(client.getTraceId())

        val validTraceId = "550e8400-e29b-41d4-a716-446655440001"
        client.withTraceId(validTraceId) {
            assertEquals(validTraceId, client.getTraceId())
        }

        // Should be restored to null after scope
        assertNull(client.getTraceId())
    }

    @Test
    fun `should generate new trace ID`() {
        client.withNewTraceId {
            val traceId = client.getTraceId()
            assertNotNull(traceId)
            assertTrue(traceId.matches(Regex("[0-9a-f-]{36}")))
        }
    }

    // ==================== Coroutine-safe Trace ID Tests ====================

    @Test
    fun `should handle suspend trace ID context`() = runBlocking {
        assertNull(client.getTraceIdSuspend())

        val validTraceId = "550e8400-e29b-41d4-a716-446655440002"
        client.withTraceIdSuspend(validTraceId) {
            assertEquals(validTraceId, client.getTraceIdSuspend())
        }

        // Should be restored to null after scope
        assertNull(client.getTraceIdSuspend())
    }

    @Test
    fun `should propagate trace ID across thread switches`() = runBlocking {
        val validTraceId = "550e8400-e29b-41d4-a716-446655440003"

        client.withTraceIdSuspend(validTraceId) {
            // Switch to different dispatcher - trace ID should be preserved
            val traceIdOnDifferentThread = withContext(Dispatchers.Default) {
                client.getTraceIdSuspend()
            }
            assertEquals(validTraceId, traceIdOnDifferentThread)
        }
    }

    @Test
    fun `should propagate trace ID to child coroutines`() = runBlocking {
        val validTraceId = "550e8400-e29b-41d4-a716-446655440004"

        client.withTraceIdSuspend(validTraceId) {
            // Launch child coroutine
            val deferredTraceId = async {
                client.getTraceIdSuspend()
            }
            assertEquals(validTraceId, deferredTraceId.await())

            // Multiple child coroutines
            coroutineScope {
                launch {
                    assertEquals(validTraceId, client.getTraceIdSuspend())
                }
                launch {
                    assertEquals(validTraceId, client.getTraceIdSuspend())
                }
            }
        }
    }

    @Test
    fun `should generate new suspend trace ID`() = runBlocking {
        client.withNewTraceIdSuspend {
            val traceId = client.getTraceIdSuspend()
            assertNotNull(traceId)
            assertTrue(traceId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        }
    }

    @Test
    fun `should handle nested suspend trace IDs`() = runBlocking {
        val outerTraceId = "550e8400-e29b-41d4-a716-446655440005"
        val innerTraceId = "550e8400-e29b-41d4-a716-446655440006"

        client.withTraceIdSuspend(outerTraceId) {
            assertEquals(outerTraceId, client.getTraceIdSuspend())

            client.withTraceIdSuspend(innerTraceId) {
                assertEquals(innerTraceId, client.getTraceIdSuspend())
            }

            // Should restore outer trace ID
            assertEquals(outerTraceId, client.getTraceIdSuspend())
        }
    }

    @Test
    fun `should validate and normalize trace ID`() {
        // Invalid trace ID should be replaced
        client.setTraceId("invalid-trace-id")
        val traceId = client.getTraceId()
        assertNotNull(traceId)
        // Should be a valid UUID
        assertTrue(traceId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `should throw BufferFullException when buffer is full`() {
        val smallBufferClient = LogTideClient(
            LogTideClientOptions(
                apiUrl = "http://localhost:8080",
                apiKey = "test_key",
                maxBufferSize = 5
            )
        )

        // Fill buffer
        repeat(5) {
            smallBufferClient.info("test", "Message $it")
        }

        // Next log should throw
        assertFailsWith<BufferFullException> {
            smallBufferClient.info("test", "Overflow message")
        }

        runBlocking {
            try {
                smallBufferClient.close()
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun `should track metrics`() {
        val metrics = client.getMetrics()
        
        assertEquals(0L, metrics.logsSent)
        assertEquals(0L, metrics.logsDropped)
        assertEquals(0L, metrics.errors)
        assertEquals(0L, metrics.retries)
        assertEquals(0.0, metrics.avgLatencyMs)
        assertEquals(0L, metrics.circuitBreakerTrips)
    }

    @Test
    fun `should reset metrics`() {
        client.info("test", "Message")
        
        client.resetMetrics()
        
        val metrics = client.getMetrics()
        assertEquals(0L, metrics.logsSent)
        assertEquals(0L, metrics.logsDropped)
    }

    @Test
    fun `should handle error serialization`() {
        val exception = RuntimeException("Test error")
        
        // Should not throw
        client.error("test-service", "Error occurred", exception)
        
        // Check metrics
        val metrics = client.getMetrics()
        assertTrue(metrics.errors == 0L) // No send errors yet
    }

    @Test
    fun `should support all log levels`() {
        client.debug("test", "Debug message")
        client.info("test", "Info message")
        client.warn("test", "Warn message")
        client.error("test", "Error message")
        client.critical("test", "Critical message")
        
        // All should be buffered
        val metrics = client.getMetrics()
        assertEquals(0L, metrics.logsSent) // Not flushed yet
    }

    @Test
    fun `should handle custom log entry`() {
        val entry = LogEntry(
            service = "custom-service",
            level = LogLevel.INFO,
            message = "Custom message",
            metadata = mapOf("custom" to "metadata")
        )
        
        client.log(entry)
        
        // Should be buffered
        val metrics = client.getMetrics()
        assertEquals(0L, metrics.logsSent)
    }
}

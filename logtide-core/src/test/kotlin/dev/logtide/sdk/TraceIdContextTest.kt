@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package dev.logtide.sdk

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext
import kotlin.test.*

/**
 * Unit tests for TraceIdContext and TraceIdElement
 */
class TraceIdContextTest {

    @BeforeEach
    fun setup() {
        // Clear ThreadLocal before each test
        threadLocalTraceId.set(null)
    }

    @AfterEach
    fun cleanup() {
        // Ensure ThreadLocal is cleared
        threadLocalTraceId.set(null)
    }

    // ==================== TraceIdElement Basic Tests ====================

    @Test
    fun `TraceIdElement should store trace ID`() {
        val element = TraceIdElement("test-trace-id")
        assertEquals("test-trace-id", element.traceId)
    }

    @Test
    fun `TraceIdElement should have correct key`() {
        val element = TraceIdElement("test-trace-id")
        assertEquals(TraceIdElement.Key, element.key)
    }

    @Test
    fun `TraceIdElement toString should include trace ID`() {
        val element = TraceIdElement("test-trace-id")
        assertTrue(element.toString().contains("test-trace-id"))
    }

    // ==================== ThreadLocal Synchronization Tests ====================

    @Test
    fun `updateThreadContext should set ThreadLocal`() = runTest {
        val element = TraceIdElement("trace-123")
        val context = coroutineContext

        assertNull(threadLocalTraceId.get())

        val previousValue = element.updateThreadContext(context)

        assertEquals("trace-123", threadLocalTraceId.get())
        assertNull(previousValue)
    }

    @Test
    fun `restoreThreadContext should restore previous value`() = runTest {
        val element = TraceIdElement("trace-123")
        val context = coroutineContext

        // Set initial value
        threadLocalTraceId.set("previous-trace")

        val previousValue = element.updateThreadContext(context)
        assertEquals("previous-trace", previousValue)
        assertEquals("trace-123", threadLocalTraceId.get())

        element.restoreThreadContext(context, previousValue)
        assertEquals("previous-trace", threadLocalTraceId.get())
    }

    @Test
    fun `restoreThreadContext should clear ThreadLocal when previous was null`() = runTest {
        val element = TraceIdElement("trace-123")
        val context = coroutineContext

        val previousValue = element.updateThreadContext(context)
        assertNull(previousValue)

        element.restoreThreadContext(context, null)
        assertNull(threadLocalTraceId.get())
    }

    // ==================== copyForChild Tests ====================

    @Test
    fun `copyForChild should create new element with same trace ID`() {
        val parent = TraceIdElement("parent-trace")
        val child = parent.copyForChild()

        assertTrue(child is TraceIdElement)
        assertEquals("parent-trace", (child as TraceIdElement).traceId)
    }

    @Test
    fun `copyForChild should create independent copy`() {
        val parent = TraceIdElement("parent-trace")
        val child = parent.copyForChild() as TraceIdElement

        // They should have the same trace ID but be different objects
        assertEquals(parent.traceId, child.traceId)
        assertNotSame(parent, child)
    }

    // ==================== mergeForChild Tests ====================

    @Test
    fun `mergeForChild should return overwriting element`() {
        val parent = TraceIdElement("parent-trace")
        val child = TraceIdElement("child-trace")

        val merged = parent.mergeForChild(child)

        assertSame(child, merged)
    }

    // ==================== Coroutine Context Integration Tests ====================

    @Test
    fun `withContext should propagate trace ID`() = runBlocking {
        val traceId = "context-trace-123"

        withContext(TraceIdElement(traceId)) {
            assertEquals(traceId, threadLocalTraceId.get())

            val contextElement = coroutineContext[TraceIdElement]
            assertNotNull(contextElement)
            assertEquals(traceId, contextElement.traceId)
        }

        // Should be cleared after withContext
        assertNull(threadLocalTraceId.get())
    }

    @Test
    fun `nested withContext should use inner trace ID`() = runBlocking {
        val outerTraceId = "outer-trace"
        val innerTraceId = "inner-trace"

        withContext(TraceIdElement(outerTraceId)) {
            assertEquals(outerTraceId, threadLocalTraceId.get())

            withContext(TraceIdElement(innerTraceId)) {
                assertEquals(innerTraceId, threadLocalTraceId.get())
            }

            // Should restore outer trace ID
            assertEquals(outerTraceId, threadLocalTraceId.get())
        }
    }

    @Test
    fun `launch should inherit trace ID from parent`() = runBlocking {
        val traceId = "parent-trace"
        var childTraceId: String? = null

        withContext(TraceIdElement(traceId)) {
            launch {
                childTraceId = threadLocalTraceId.get()
            }.join()
        }

        assertEquals(traceId, childTraceId)
    }

    @Test
    fun `async should inherit trace ID from parent`() = runBlocking {
        val traceId = "async-parent-trace"

        val result = withContext(TraceIdElement(traceId)) {
            async {
                threadLocalTraceId.get()
            }.await()
        }

        assertEquals(traceId, result)
    }

    @Test
    fun `multiple child coroutines should all have same trace ID`() = runBlocking {
        val traceId = "multi-child-trace"
        val results = mutableListOf<String?>()

        withContext(TraceIdElement(traceId)) {
            val jobs = (1..5).map { i ->
                launch {
                    results.add(threadLocalTraceId.get())
                }
            }
            jobs.forEach { it.join() }
        }

        assertEquals(5, results.size)
        assertTrue(results.all { it == traceId })
    }

    // ==================== Dispatcher Switch Tests ====================

    @Test
    fun `trace ID should survive dispatcher switch`() = runBlocking {
        val traceId = "dispatcher-switch-trace"
        var traceIdOnDefault: String? = null
        var traceIdOnIO: String? = null

        withContext(TraceIdElement(traceId)) {
            traceIdOnDefault = withContext(Dispatchers.Default) {
                threadLocalTraceId.get()
            }

            traceIdOnIO = withContext(Dispatchers.IO) {
                threadLocalTraceId.get()
            }
        }

        assertEquals(traceId, traceIdOnDefault)
        assertEquals(traceId, traceIdOnIO)
    }

    @Test
    fun `trace ID should be restored after dispatcher switch`() = runBlocking {
        val traceId = "restore-after-switch-trace"

        withContext(TraceIdElement(traceId)) {
            // Switch to different dispatcher
            withContext(Dispatchers.IO) {
                assertEquals(traceId, threadLocalTraceId.get())
            }

            // Should still have trace ID after returning
            assertEquals(traceId, threadLocalTraceId.get())
        }
    }

    // ==================== currentTraceId Helper Tests ====================

    @Test
    fun `currentTraceId should return trace ID from context`() = runBlocking {
        val traceId = "current-trace-id"

        withContext(TraceIdElement(traceId)) {
            assertEquals(traceId, currentTraceId())
        }
    }

    @Test
    fun `currentTraceId should fallback to ThreadLocal`() = runBlocking {
        threadLocalTraceId.set("fallback-trace")

        // Without TraceIdElement in context, should use ThreadLocal
        val result = currentTraceId()
        assertEquals("fallback-trace", result)
    }

    @Test
    fun `currentTraceId should return null when no trace ID set`() = runBlocking {
        assertNull(currentTraceId())
    }

    @Test
    fun `currentTraceId should prefer context over ThreadLocal`() = runBlocking {
        threadLocalTraceId.set("threadlocal-trace")

        withContext(TraceIdElement("context-trace")) {
            // Context should take precedence
            assertEquals("context-trace", currentTraceId())
        }
    }

    // ==================== Edge Cases ====================

    @Test
    fun `empty trace ID should be handled`() = runBlocking {
        withContext(TraceIdElement("")) {
            assertEquals("", threadLocalTraceId.get())
            assertEquals("", currentTraceId())
        }
    }

    @Test
    fun `long trace ID should be handled`() = runBlocking {
        val longTraceId = "a".repeat(1000)

        withContext(TraceIdElement(longTraceId)) {
            assertEquals(longTraceId, currentTraceId())
        }
    }

    @Test
    fun `concurrent coroutines should have isolated contexts`() = runBlocking {
        val results = mutableMapOf<Int, String?>()

        coroutineScope {
            (1..10).map { i ->
                launch {
                    withContext(TraceIdElement("trace-$i")) {
                        delay(10) // Simulate some work
                        results[i] = currentTraceId()
                    }
                }
            }
        }

        // Each coroutine should have its own trace ID
        (1..10).forEach { i ->
            assertEquals("trace-$i", results[i])
        }
    }

    @Test
    fun `cancellation should properly restore ThreadLocal`() = runBlocking {
        val traceId = "cancellation-trace"

        try {
            withContext(TraceIdElement(traceId)) {
                assertEquals(traceId, threadLocalTraceId.get())
                throw CancellationException("Test cancellation")
            }
        } catch (e: CancellationException) {
            // Expected
        }

        // ThreadLocal should be restored
        assertNull(threadLocalTraceId.get())
    }
}

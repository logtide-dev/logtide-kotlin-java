package dev.logtide.sdk.exceptions

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for LogTide SDK exceptions
 */
class ExceptionTest {

    // ==================== LogTideException Tests ====================

    @Test
    fun `LogTideException should store message`() {
        val exception = LogTideException("Test error message")
        assertEquals("Test error message", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `LogTideException should store message and cause`() {
        val cause = RuntimeException("Root cause")
        val exception = LogTideException("Test error message", cause)

        assertEquals("Test error message", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `LogTideException should extend Exception`() {
        val exception = LogTideException("Test message")
        assertTrue(exception is Exception)
    }

    @Test
    fun `LogTideException should be throwable and catchable`() {
        val caught = assertFailsWith<LogTideException> {
            throw LogTideException("Test exception")
        }
        assertEquals("Test exception", caught.message)
    }

    // ==================== CircuitBreakerOpenException Tests ====================

    @Test
    fun `CircuitBreakerOpenException should have default message`() {
        val exception = CircuitBreakerOpenException()
        assertEquals("Circuit breaker is OPEN - requests are blocked", exception.message)
    }

    @Test
    fun `CircuitBreakerOpenException should accept custom message`() {
        val exception = CircuitBreakerOpenException("Custom circuit breaker message")
        assertEquals("Custom circuit breaker message", exception.message)
    }

    @Test
    fun `CircuitBreakerOpenException should extend LogTideException`() {
        val exception = CircuitBreakerOpenException()
        assertTrue(exception is LogTideException)
        assertTrue(exception is Exception)
    }

    @Test
    fun `CircuitBreakerOpenException should be catchable as LogTideException`() {
        val caught = assertFailsWith<LogTideException> {
            throw CircuitBreakerOpenException()
        }
        assertTrue(caught is CircuitBreakerOpenException)
    }

    // ==================== BufferFullException Tests ====================

    @Test
    fun `BufferFullException should have default message`() {
        val exception = BufferFullException()
        assertEquals("Log buffer is full - log entry dropped", exception.message)
    }

    @Test
    fun `BufferFullException should accept custom message`() {
        val exception = BufferFullException("Buffer overflow with 10000 entries")
        assertEquals("Buffer overflow with 10000 entries", exception.message)
    }

    @Test
    fun `BufferFullException should extend LogTideException`() {
        val exception = BufferFullException()
        assertTrue(exception is LogTideException)
        assertTrue(exception is Exception)
    }

    @Test
    fun `BufferFullException should be catchable as LogTideException`() {
        val caught = assertFailsWith<LogTideException> {
            throw BufferFullException()
        }
        assertTrue(caught is BufferFullException)
    }

    // ==================== Exception Hierarchy Tests ====================

    @Test
    fun `all exceptions should be part of the same hierarchy`() {
        val logTideException = LogTideException("test")
        val circuitBreakerException = CircuitBreakerOpenException()
        val bufferFullException = BufferFullException()

        // All should be catchable as Exception
        assertTrue(logTideException is Exception)
        assertTrue(circuitBreakerException is Exception)
        assertTrue(bufferFullException is Exception)

        // Specific exceptions should be catchable as LogTideException
        assertTrue(circuitBreakerException is LogTideException)
        assertTrue(bufferFullException is LogTideException)
    }

    @Test
    fun `exceptions can be caught with specific handler`() {
        fun throwException(type: Int) {
            when (type) {
                1 -> throw CircuitBreakerOpenException()
                2 -> throw BufferFullException()
                else -> throw LogTideException("Generic error")
            }
        }

        var circuitBreakerCaught = false
        var bufferFullCaught = false

        try {
            throwException(1)
        } catch (e: CircuitBreakerOpenException) {
            circuitBreakerCaught = true
        } catch (e: LogTideException) {
            fail("Should have caught CircuitBreakerOpenException")
        }

        try {
            throwException(2)
        } catch (e: BufferFullException) {
            bufferFullCaught = true
        } catch (e: LogTideException) {
            fail("Should have caught BufferFullException")
        }

        assertTrue(circuitBreakerCaught)
        assertTrue(bufferFullCaught)
    }

    @Test
    fun `exceptions should preserve stack trace`() {
        val exception = LogTideException("Test")
        val stackTrace = exception.stackTrace
        assertTrue(stackTrace.isNotEmpty())
        assertTrue(stackTrace.any { it.className.contains("ExceptionTest") })
    }

    @Test
    fun `exceptions with cause should have correct cause chain`() {
        val rootCause = IllegalArgumentException("Invalid argument")
        val cause = RuntimeException("Intermediate error", rootCause)
        val exception = LogTideException("Top level error", cause)

        assertEquals("Top level error", exception.message)
        assertEquals("Intermediate error", exception.cause?.message)
        assertEquals("Invalid argument", exception.cause?.cause?.message)
    }
}

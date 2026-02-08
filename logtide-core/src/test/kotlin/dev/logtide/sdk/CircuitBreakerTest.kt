package dev.logtide.sdk

import dev.logtide.sdk.enums.CircuitState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for CircuitBreaker
 */
class CircuitBreakerTest {

    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun setup() {
        circuitBreaker = CircuitBreaker(threshold = 3, resetMs = 1000)
    }

    @Test
    fun `should start in CLOSED state`() {
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        assertTrue(circuitBreaker.canAttempt())
    }

    @Test
    fun `should allow attempts when CLOSED`() {
        assertTrue(circuitBreaker.canAttempt())
        assertTrue(circuitBreaker.canAttempt())
        assertTrue(circuitBreaker.canAttempt())
    }

    @Test
    fun `should open circuit after threshold failures`() {
        // Record failures below threshold
        circuitBreaker.recordFailure()
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        assertTrue(circuitBreaker.canAttempt())

        circuitBreaker.recordFailure()
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        assertTrue(circuitBreaker.canAttempt())

        // Third failure should open circuit (threshold = 3)
        circuitBreaker.recordFailure()
        assertEquals(CircuitState.OPEN, circuitBreaker.getState())
        assertFalse(circuitBreaker.canAttempt())
    }

    @Test
    fun `should block attempts when OPEN`() {
        // Open the circuit
        repeat(3) { circuitBreaker.recordFailure() }
        assertEquals(CircuitState.OPEN, circuitBreaker.getState())

        // Should block attempts
        assertFalse(circuitBreaker.canAttempt())
        assertFalse(circuitBreaker.canAttempt())
    }

    @Test
    fun `should transition to HALF_OPEN after reset timeout`() {
        // Open the circuit
        repeat(3) { circuitBreaker.recordFailure() }
        assertEquals(CircuitState.OPEN, circuitBreaker.getState())

        // Wait for reset timeout
        Thread.sleep(1100) // resetMs = 1000, add buffer

        // Should transition to HALF_OPEN and allow one attempt
        assertTrue(circuitBreaker.canAttempt())
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState())
    }

    @Test
    fun `should close circuit on success after HALF_OPEN`() {
        // Open the circuit
        repeat(3) { circuitBreaker.recordFailure() }
        
        // Wait and transition to HALF_OPEN
        Thread.sleep(1100)
        circuitBreaker.canAttempt()
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState())

        // Record success
        circuitBreaker.recordSuccess()
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        assertTrue(circuitBreaker.canAttempt())
    }

    @Test
    fun `should reopen circuit on failure in HALF_OPEN state`() {
        // Open the circuit
        repeat(3) { circuitBreaker.recordFailure() }
        
        // Wait and transition to HALF_OPEN
        Thread.sleep(1100)
        circuitBreaker.canAttempt()
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState())

        // Record another failure
        circuitBreaker.recordFailure()
        assertEquals(CircuitState.OPEN, circuitBreaker.getState())
        assertFalse(circuitBreaker.canAttempt())
    }

    @Test
    fun `should reset failure count on success`() {
        // Record some failures (but not enough to open)
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())

        // Record success - should reset count
        circuitBreaker.recordSuccess()
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())

        // Now we can record 2 more failures without opening
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        assertTrue(circuitBreaker.canAttempt())
    }
}

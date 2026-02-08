package dev.logtide.sdk

import dev.logtide.sdk.enums.CircuitState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit Breaker pattern implementation
 * 
 * Prevents cascading failures by temporarily blocking requests after threshold failures.
 * 
 * States:
 * - CLOSED: Normal operation, requests are allowed
 * - OPEN: Too many failures, requests are blocked
 * - HALF_OPEN: Testing if the service has recovered
 */
class CircuitBreaker(
    private val threshold: Int,
    private val resetMs: Long
) {
    private val state = AtomicReference(CircuitState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)

    /**
     * Record a successful operation
     * Resets failure count and closes the circuit
     */
    fun recordSuccess() {
        failureCount.set(0)
        state.set(CircuitState.CLOSED)
    }

    /**
     * Record a failed operation
     * Increments failure count and opens circuit if threshold reached
     */
    fun recordFailure() {
        val failures = failureCount.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())

        if (failures >= threshold) {
            state.set(CircuitState.OPEN)
        }
    }

    /**
     * Check if an attempt can be made
     * 
     * - CLOSED: always allow
     * - OPEN: allow after resetMs has elapsed (transition to HALF_OPEN)
     * - HALF_OPEN: allow one attempt
     */
    fun canAttempt(): Boolean {
        val currentState = state.get()

        if (currentState == CircuitState.CLOSED) {
            return true
        }

        if (currentState == CircuitState.OPEN) {
            val now = System.currentTimeMillis()
            val lastFailure = lastFailureTime.get()
            
            if (lastFailure > 0 && (now - lastFailure) >= resetMs) {
                state.set(CircuitState.HALF_OPEN)
                return true
            }
            return false
        }

        // HALF_OPEN state - allow one attempt
        return true
    }

    /**
     * Get current circuit state
     */
    fun getState(): CircuitState = state.get()
}

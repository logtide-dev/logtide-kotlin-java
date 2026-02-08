package dev.logtide.sdk.enums

/**
 * Circuit breaker states
 * 
 * - CLOSED: Normal operation, requests are allowed
 * - OPEN: Too many failures, requests are blocked
 * - HALF_OPEN: Testing if the service has recovered
 */
enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

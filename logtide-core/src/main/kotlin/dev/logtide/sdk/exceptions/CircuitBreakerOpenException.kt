package dev.logtide.sdk.exceptions

/**
 * Exception thrown when circuit breaker is in OPEN state
 */
class CircuitBreakerOpenException(
    message: String = "Circuit breaker is OPEN - requests are blocked"
) : LogTideException(message)

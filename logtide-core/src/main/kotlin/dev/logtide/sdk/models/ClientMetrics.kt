package dev.logtide.sdk.models

/**
 * SDK internal metrics
 */
data class ClientMetrics(
    val logsSent: Long = 0,
    val logsDropped: Long = 0,
    val errors: Long = 0,
    val retries: Long = 0,
    val avgLatencyMs: Double = 0.0,
    val circuitBreakerTrips: Long = 0
)

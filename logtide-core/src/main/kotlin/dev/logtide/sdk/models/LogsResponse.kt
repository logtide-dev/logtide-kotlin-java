package dev.logtide.sdk.models

import kotlinx.serialization.Serializable

/**
 * Response from logs query
 */
@Serializable
data class LogsResponse(
    val logs: List<LogEntry>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

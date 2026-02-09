package dev.logtide.sdk.models

import dev.logtide.sdk.enums.LogLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Single log entry
 */
@Serializable
data class LogEntry(
    val service: String,
    val level: LogLevel,
    val message: String,
    val time: String = Instant.now().toString(),
    val metadata: Map<String, @Serializable(with = AnyValueSerializer::class) Any>? = null,
    @SerialName("trace_id")
    val traceId: String? = null
)

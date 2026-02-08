package dev.logtide.sdk.models

import dev.logtide.sdk.enums.LogLevel
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Options for querying logs
 */
@Serializable
data class QueryOptions(
    val service: String? = null,
    val level: LogLevel? = null,
    @Serializable(with = InstantSerializer::class)
    val from: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val to: Instant? = null,
    val q: String? = null,
    val limit: Int? = null,
    val offset: Int? = null
)

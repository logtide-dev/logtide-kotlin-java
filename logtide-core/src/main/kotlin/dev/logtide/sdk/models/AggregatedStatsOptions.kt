package dev.logtide.sdk.models

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Options for aggregated statistics query
 */
@Serializable
data class AggregatedStatsOptions(
    @Serializable(with = InstantSerializer::class)
    val from: Instant,
    @Serializable(with = InstantSerializer::class)
    val to: Instant,
    val interval: String? = null, // '1m' | '5m' | '1h' | '1d'
    val service: String? = null
)

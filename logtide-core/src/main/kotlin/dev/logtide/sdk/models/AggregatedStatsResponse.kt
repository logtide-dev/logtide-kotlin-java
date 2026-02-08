package dev.logtide.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Aggregated statistics response
 */
@Serializable
data class AggregatedStatsResponse(
    val timeseries: List<TimeSeriesBucket>,
    @SerialName("top_services")
    val topServices: List<ServiceCount>,
    @SerialName("top_errors")
    val topErrors: List<ErrorCount>
)

@Serializable
data class TimeSeriesBucket(
    val bucket: String,
    val total: Int,
    @SerialName("by_level")
    val byLevel: Map<String, Int>
)

@Serializable
data class ServiceCount(
    val service: String,
    val count: Int
)

@Serializable
data class ErrorCount(
    val message: String,
    val count: Int
)

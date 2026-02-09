package dev.logtide.sdk.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Log severity levels
 */
@Serializable
enum class LogLevel(val value: String) {
    @SerialName("debug")
    DEBUG("debug"),
    
    @SerialName("info")
    INFO("info"),
    
    @SerialName("warn")
    WARN("warn"),
    
    @SerialName("error")
    ERROR("error"),
    
    @SerialName("critical")
    CRITICAL("critical");
    
    override fun toString(): String = value
}

package dev.logtide.sdk.exceptions

/**
 * Base exception for LogTide SDK errors
 */
open class LogTideException(message: String, cause: Throwable? = null) : Exception(message, cause)

package dev.logtide.sdk.exceptions

/**
 * Exception thrown when buffer is full and logs are dropped
 */
class BufferFullException(
    message: String = "Log buffer is full - log entry dropped"
) : LogTideException(message)

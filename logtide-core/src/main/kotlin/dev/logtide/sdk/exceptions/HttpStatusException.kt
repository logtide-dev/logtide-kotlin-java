package dev.logtide.sdk.exceptions

/**
 * Thrown when the ingest endpoint replies with a non-2xx status.
 * Carries the status code so the retry logic can distinguish retryable
 * failures (408, 429, 5xx) from permanent ones (other 4xx).
 */
class HttpStatusException(
    val statusCode: Int,
    message: String,
    /** Parsed Retry-After header in milliseconds, when present. */
    val retryAfterMs: Long? = null,
) : LogTideException(message) {
    val isRetryable: Boolean
        get() = statusCode == 408 || statusCode == 429 || statusCode >= 500
}

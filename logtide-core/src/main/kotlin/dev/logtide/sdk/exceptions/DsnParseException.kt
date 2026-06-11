package dev.logtide.sdk.exceptions

/**
 * Thrown when a DSN string cannot be parsed. Configuration errors must
 * fail loudly at init time.
 */
class DsnParseException(message: String) : LogTideException(message)

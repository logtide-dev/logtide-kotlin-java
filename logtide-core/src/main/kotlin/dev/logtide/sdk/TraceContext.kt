package dev.logtide.sdk

import java.security.SecureRandom

/**
 * Parsed W3C `traceparent` header.
 */
data class ParsedTraceContext(
    val traceId: String,
    val spanId: String,
    val sampled: Boolean,
)

/**
 * W3C Trace Context utilities (https://www.w3.org/TR/trace-context/).
 *
 * `traceparent` is the interoperable propagation header; the legacy
 * `X-Trace-ID` header is still accepted as a fallback but is deprecated.
 */
object TraceContext {

    const val TRACEPARENT_HEADER: String = "traceparent"
    const val LEGACY_TRACE_HEADER: String = "X-Trace-ID"

    private val TRACEPARENT_REGEX =
        Regex("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")

    private val random = SecureRandom()
    private const val HEX = "0123456789abcdef"

    private fun randomHex(bytes: Int): String {
        val buf = ByteArray(bytes)
        random.nextBytes(buf)
        val sb = StringBuilder(bytes * 2)
        for (b in buf) {
            sb.append(HEX[(b.toInt() shr 4) and 0x0f]).append(HEX[b.toInt() and 0x0f])
        }
        return sb.toString()
    }

    /** New 32-char lowercase-hex W3C trace id (never all-zero). */
    fun generateTraceId(): String {
        while (true) {
            val id = randomHex(16)
            if (id != "0".repeat(32)) return id
        }
    }

    /** New 16-char lowercase-hex W3C span id (never all-zero). */
    fun generateSpanId(): String {
        while (true) {
            val id = randomHex(8)
            if (id != "0".repeat(16)) return id
        }
    }

    /**
     * Parse a `traceparent` header value. Returns null for missing or
     * malformed values (wrong lengths, uppercase hex, all-zero ids, the
     * forbidden `ff` version).
     */
    fun parseTraceparent(header: String?): ParsedTraceContext? {
        if (header.isNullOrBlank()) return null
        val match = TRACEPARENT_REGEX.find(header.trim()) ?: return null
        val (version, traceId, spanId, flags) = match.destructured
        if (version == "ff") return null
        if (traceId == "0".repeat(32) || spanId == "0".repeat(16)) return null
        val sampled = (flags.toInt(16) and 0x01) == 0x01
        return ParsedTraceContext(traceId, spanId, sampled)
    }

    /** Build a `traceparent` header value (version 00). */
    fun formatTraceparent(traceId: String, spanId: String, sampled: Boolean = true): String =
        "00-$traceId-$spanId-${if (sampled) "01" else "00"}"

    /**
     * Resolve the inbound trace id: valid `traceparent` first, then the
     * legacy `X-Trace-ID` value, otherwise a newly generated W3C trace id.
     */
    fun resolveTraceId(traceparent: String?, legacy: String?): String {
        parseTraceparent(traceparent)?.let { return it.traceId }
        if (!legacy.isNullOrBlank()) return legacy
        return generateTraceId()
    }
}


/**
 * Pluggable lookup for the active span's trace context, used in the
 * resolution order (explicit -> active span -> scope -> client context).
 * Registered by the `logtide-otel` module when OpenTelemetry is configured.
 */
object ActiveTraceContext {

    @Volatile
    var provider: (() -> Pair<String, String>?)? = null

    /** The active span's (traceId, spanId), or null. Never throws. */
    fun current(): Pair<String, String>? = try {
        provider?.invoke()
    } catch (_: Exception) {
        null
    }
}

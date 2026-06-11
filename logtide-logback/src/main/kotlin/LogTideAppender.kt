package dev.logtide.sdk.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.enums.LogLevel
import dev.logtide.sdk.models.LogEntry
import dev.logtide.sdk.models.LogTideClientOptions
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Logback appender that routes logging events through a [LogTideClient],
 * so existing SLF4J/Logback logging flows to LogTide without code changes.
 *
 * XML configuration:
 * ```xml
 * <appender name="LOGTIDE" class="dev.logtide.sdk.logback.LogTideAppender">
 *     <apiUrl>https://logs.example.com</apiUrl>
 *     <apiKey>lp_your_api_key</apiKey>
 *     <serviceName>my-service</serviceName>
 * </appender>
 * ```
 *
 * Alternatively, set [client] programmatically to share an existing client
 * (the appender will not close a client it did not create).
 *
 * MDC values become entry metadata; the `trace_id` (or `traceId`) MDC key is
 * promoted to the top-level trace id. Throwables are serialized to the
 * platform's structured exception format via the client.
 */
class LogTideAppender : AppenderBase<ILoggingEvent>() {

    /** Base URL of the LogTide instance (used when no client is injected). */
    var apiUrl: String? = null

    /** API key (used when no client is injected). */
    var apiKey: String? = null

    /** Service name stamped on every entry. */
    var serviceName: String = "logback"

    /** Batch size for the internally-created client. */
    var batchSize: Int = 100

    /** Flush interval, in seconds, for the internally-created client. */
    var flushIntervalSeconds: Long = 5

    /** Shared client. When null, one is built from the properties above. */
    var client: LogTideClient? = null

    private var ownsClient = false

    override fun start() {
        if (client == null) {
            val url = apiUrl
            val key = apiKey
            if (url.isNullOrBlank() || key.isNullOrBlank()) {
                addError("LogTideAppender requires apiUrl and apiKey (or an injected client); not starting")
                return
            }
            client = LogTideClient(
                LogTideClientOptions(
                    apiUrl = url,
                    apiKey = key,
                    batchSize = batchSize,
                    flushInterval = flushIntervalSeconds.seconds,
                )
            )
            ownsClient = true
        }
        super.start()
    }

    override fun stop() {
        if (!isStarted) return
        super.stop()
        val c = client ?: return
        if (ownsClient) {
            // close() flushes remaining logs
            runCatching { runBlocking { c.close() } }
        } else {
            runCatching { runBlocking { c.flush() } }
        }
    }

    override fun append(event: ILoggingEvent) {
        val c = client ?: return

        val metadata = mutableMapOf<String, Any>(
            "logger" to event.loggerName,
            "thread" to event.threadName,
        )

        var traceId: String? = null
        // mdcPropertyMap NPEs on contexts without an MDCAdapter (standalone
        // LoggerContext, some bindings) — never let that break appending.
        val mdc = runCatching { event.mdcPropertyMap }.getOrNull()
        mdc?.forEach { (key, value) ->
            if (value == null) return@forEach
            when (key) {
                "trace_id", "traceId" -> traceId = value
                else -> metadata[key] = value
            }
        }

        // SLF4J 2.x fluent-API key/value pairs
        event.keyValuePairs?.forEach { pair ->
            pair.value?.let { metadata[pair.key] = it }
        }

        (event.throwableProxy as? ThrowableProxy)?.throwable?.let { throwable ->
            c.metadataOrErrorToMap(throwable)?.let { metadata.putAll(it) }
        }

        c.log(
            LogEntry(
                service = serviceName,
                level = mapLevel(event.level),
                message = event.formattedMessage ?: "",
                time = Instant.ofEpochMilli(event.timeStamp).toString(),
                metadata = metadata,
                traceId = traceId,
            )
        )
    }

    private fun mapLevel(level: Level?): LogLevel = when (level) {
        Level.ERROR -> LogLevel.ERROR
        Level.WARN -> LogLevel.WARN
        Level.INFO -> LogLevel.INFO
        else -> LogLevel.DEBUG // DEBUG, TRACE, ALL
    }
}

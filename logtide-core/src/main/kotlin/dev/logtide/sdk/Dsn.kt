package dev.logtide.sdk

import dev.logtide.sdk.exceptions.DsnParseException
import java.net.URI

/**
 * Components extracted from a DSN.
 */
data class DsnParts(
    val apiUrl: String,
    val apiKey: String,
)

/**
 * DSN parsing (spec 002 §3).
 *
 * A DSN is a single connection string carrying endpoint and API key:
 * `https://lp_abc123@logs.example.com[/base-path]`. The path, when present,
 * is a base-path prefix (reverse-proxied installs) and is preserved.
 */
object Dsn {

    fun parse(dsn: String): DsnParts {
        if (dsn.isBlank()) throw DsnParseException("DSN must not be blank")

        val uri = try {
            URI(dsn)
        } catch (e: Exception) {
            throw DsnParseException("Malformed DSN: ${e.message}")
        }

        if (uri.scheme != "http" && uri.scheme != "https") {
            throw DsnParseException("DSN scheme must be http or https, got '${uri.scheme}'")
        }
        val apiKey = uri.userInfo
        if (apiKey.isNullOrBlank()) {
            throw DsnParseException("DSN is missing the API key (expected scheme://key@host)")
        }
        val host = uri.host ?: throw DsnParseException("DSN is missing the host")

        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.path?.trimEnd('/') ?: ""

        return DsnParts(apiUrl = "${uri.scheme}://$host$portPart$path", apiKey = apiKey)
    }
}

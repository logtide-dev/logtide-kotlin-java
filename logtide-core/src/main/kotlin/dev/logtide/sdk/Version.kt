package dev.logtide.sdk

/**
 * SDK identity stamped on every entry as `metadata.sdk` (spec 003 §3).
 * Keep [SDK_VERSION] in sync with `projectVersion` in gradle.properties.
 */
internal const val SDK_NAME = "logtide-kotlin"
internal const val SDK_VERSION = "0.9.4"

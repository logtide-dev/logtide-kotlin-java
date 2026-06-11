package dev.logtide.sdk

import dev.logtide.sdk.models.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.ArrayDeque

/**
 * User context (spec 003 §6). All fields optional.
 */
data class LogTideUser(
    val id: String? = null,
    val email: String? = null,
    val username: String? = null,
    val ip: String? = null,
) {
    fun toMap(): Map<String, String> = buildMap {
        id?.let { put("id", it) }
        email?.let { put("email", it) }
        username?.let { put("username", it) }
        ip?.let { put("ip", it) }
    }
}

/**
 * A discrete event recorded before an entry (spec 003 §5).
 */
data class Breadcrumb(
    val message: String = "",
    val type: String = "custom", // http | navigation | ui | console | query | error | custom
    val category: String? = null,
    val data: Map<String, Any>? = null,
    val level: String = "info",
    val timestamp: String = Instant.now().toString(),
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("message", message)
        put("type", type)
        put("level", level)
        put("timestamp", timestamp)
        category?.let { put("category", it) }
        data?.takeIf { it.isNotEmpty() }?.let { put("data", it) }
    }
}

/**
 * Per-request/per-task context merged into every captured entry.
 *
 * Scope state (tags, extras, user, breadcrumbs, session and trace context)
 * is applied by [LogTideClient.log]; entry-level values win on collision.
 * Thread-safe; use [withScope] for per-request isolation.
 */
class Scope(private val maxBreadcrumbs: Int = DEFAULT_MAX_BREADCRUMBS) {

    companion object {
        const val DEFAULT_MAX_BREADCRUMBS = 100
    }

    private val lock = Any()
    private val tags = mutableMapOf<String, String>()
    private val extra = mutableMapOf<String, Any>()
    private var user: LogTideUser? = null
    private var sessionId: String? = null
    private var traceId: String? = null
    private var spanId: String? = null
    private val breadcrumbs = ArrayDeque<Breadcrumb>()

    fun setTag(key: String, value: String): Unit = synchronized(lock) { tags[key] = value }

    fun removeTag(key: String): Unit = synchronized(lock) { tags.remove(key); Unit }

    fun setExtra(key: String, value: Any): Unit = synchronized(lock) { extra[key] = value }

    fun setUser(user: LogTideUser?): Unit = synchronized(lock) { this.user = user }

    fun setSessionId(sessionId: String?): Unit = synchronized(lock) { this.sessionId = sessionId }

    fun setTraceContext(traceId: String?, spanId: String? = null): Unit = synchronized(lock) {
        this.traceId = traceId
        this.spanId = spanId
    }

    fun addBreadcrumb(crumb: Breadcrumb): Unit = synchronized(lock) {
        breadcrumbs.addLast(crumb)
        while (breadcrumbs.size > maxBreadcrumbs) breadcrumbs.removeFirst()
    }

    fun clearBreadcrumbs(): Unit = synchronized(lock) { breadcrumbs.clear() }

    /** Deep copy, safe for independent mutation. */
    fun clone(): Scope = synchronized(lock) {
        val copy = Scope(maxBreadcrumbs)
        copy.tags.putAll(tags)
        copy.extra.putAll(extra)
        copy.user = user
        copy.sessionId = sessionId
        copy.traceId = traceId
        copy.spanId = spanId
        copy.breadcrumbs.addAll(breadcrumbs)
        copy
    }

    /** Merge scope state into a copy of the entry. Entry-level values win. */
    fun applyToEntry(entry: LogEntry): LogEntry = synchronized(lock) {
        var metadata: Map<String, Any> = entry.metadata ?: emptyMap()

        if (extra.isNotEmpty()) {
            metadata = extra + metadata
        }
        if (tags.isNotEmpty() && "tags" !in metadata) {
            metadata = metadata + ("tags" to tags.toMap())
        }
        user?.toMap()?.takeIf { it.isNotEmpty() }?.let {
            if ("user" !in metadata) metadata = metadata + ("user" to it)
        }
        if (breadcrumbs.isNotEmpty() && "breadcrumbs" !in metadata) {
            metadata = metadata + ("breadcrumbs" to breadcrumbs.map { it.toMap() })
        }

        entry.copy(
            metadata = metadata.takeIf { it.isNotEmpty() },
            sessionId = entry.sessionId ?: sessionId,
            traceId = entry.traceId ?: traceId,
            spanId = entry.spanId ?: spanId,
        )
    }
}

/**
 * Holder for the current scope. ThreadLocal-based; coroutines hopping
 * threads should use [withScopeSuspend].
 */
object ScopeContext {

    internal val threadLocalScope = ThreadLocal<Scope?>()

    /** The current scope, creating the root scope for this thread on first use. */
    fun current(): Scope {
        var scope = threadLocalScope.get()
        if (scope == null) {
            scope = Scope()
            threadLocalScope.set(scope)
        }
        return scope
    }

    /** Clears the current thread's scope (mainly for tests). */
    fun clear(): Unit = threadLocalScope.remove()

    /**
     * Activate [scope] on this thread, returning the previous scope (or
     * null). Pair with [restore] in a finally block; prefer [withScope]
     * when the work is a single block.
     */
    fun activate(scope: Scope): Scope? {
        val previous = threadLocalScope.get()
        threadLocalScope.set(scope)
        return previous
    }

    /** Restore a scope previously returned by [activate]. */
    fun restore(previous: Scope?) {
        if (previous == null) threadLocalScope.remove() else threadLocalScope.set(previous)
    }
}

/**
 * Activate a clone of the current scope for the duration of [block].
 * Mutations inside the block do not leak to the outer scope.
 */
fun <T> withScope(block: (Scope) -> T): T {
    val previous = ScopeContext.threadLocalScope.get()
    val scope = (previous ?: ScopeContext.current()).clone()
    ScopeContext.threadLocalScope.set(scope)
    try {
        return block(scope)
    } finally {
        ScopeContext.threadLocalScope.set(previous)
    }
}

/**
 * Coroutine-safe variant of [withScope]: the scope follows the coroutine
 * across thread switches and into child coroutines.
 */
suspend fun <T> withScopeSuspend(block: suspend CoroutineScope.(Scope) -> T): T {
    val scope = ScopeContext.current().clone()
    return withContext(ScopeContext.threadLocalScope.asContextElement(scope)) {
        coroutineScope { block(scope) }
    }
}

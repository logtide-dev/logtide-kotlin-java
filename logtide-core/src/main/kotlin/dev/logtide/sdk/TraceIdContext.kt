@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package dev.logtide.sdk

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * ThreadLocal condiviso per il trace ID
 * Usato sia da TraceIdElement (coroutine) che da LogTideClient (compatibilità)
 */
internal val threadLocalTraceId = ThreadLocal<String?>()

/**
 * CoroutineContext element for propagating trace ID across coroutines.
 *
 * This element ensures that the trace ID is properly propagated when:
 * - A coroutine is suspended on one thread and resumed on another
 * - Child coroutines are created with launch/async
 * - Using withContext to switch dispatchers
 *
 * It also synchronizes with ThreadLocal for backwards compatibility
 * with non-suspend code.
 *
 * Usage:
 * ```kotlin
 * withContext(TraceIdElement("my-trace-id")) {
 *     // All coroutines here will have access to the trace ID
 *     launch { /* trace ID is propagated */ }
 *     async { /* trace ID is propagated */ }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class TraceIdElement(
    val traceId: String
) : CopyableThreadContextElement<String?> {

    companion object Key : CoroutineContext.Key<TraceIdElement>

    override val key: CoroutineContext.Key<TraceIdElement> = Key

    /**
     * Called when the coroutine starts executing on a thread.
     * Saves the current ThreadLocal value and sets the new trace ID.
     */
    override fun updateThreadContext(context: CoroutineContext): String? {
        val previous = threadLocalTraceId.get()
        threadLocalTraceId.set(traceId)
        return previous
    }

    /**
     * Called when the coroutine suspends or completes on a thread.
     * Restores the previous ThreadLocal value.
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: String?) {
        threadLocalTraceId.set(oldState)
    }

    /**
     * Called when a child coroutine is created (launch/async).
     * Returns a copy of this element for the child coroutine.
     */
    override fun copyForChild(): CopyableThreadContextElement<String?> {
        return TraceIdElement(traceId)
    }

    /**
     * Called when merging context elements for a child coroutine.
     * If the child has its own TraceIdElement, use that instead.
     */
    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        return overwritingElement
    }

    override fun toString(): String = "TraceIdElement(traceId=$traceId)"
}

/**
 * Get the current trace ID from the coroutine context.
 * Falls back to ThreadLocal for backwards compatibility.
 *
 * Usage:
 * ```kotlin
 * suspend fun myFunction() {
 *     val traceId = currentTraceId()
 *     // Use trace ID for logging, propagation, etc.
 * }
 * ```
 */
suspend fun currentTraceId(): String? {
    return kotlin.coroutines.coroutineContext[TraceIdElement]?.traceId
        ?: threadLocalTraceId.get()
}

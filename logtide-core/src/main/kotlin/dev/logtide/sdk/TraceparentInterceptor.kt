package dev.logtide.sdk

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that injects the W3C `traceparent` header on outbound
 * requests, propagating the current trace context to downstream services
 * (spec 005 §2, conformance C25):
 *
 * ```kotlin
 * val httpClient = OkHttpClient.Builder()
 *     .addInterceptor(TraceparentInterceptor())
 *     .build()
 * ```
 *
 * Source order: active OTel span (when `logtide-otel` is configured), then
 * the current scope's trace context (a scope without a span id gets a fresh
 * one). Requests with no active trace context, or with a `traceparent`
 * header already set, pass through untouched.
 */
class TraceparentInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(TraceContext.TRACEPARENT_HEADER) != null) {
            return chain.proceed(request)
        }

        var traceId: String?
        var spanId: String?
        val active = ActiveTraceContext.current()
        if (active != null) {
            traceId = active.first
            spanId = active.second
        } else {
            val scope = ScopeContext.threadLocalScope.get()
                ?: return chain.proceed(request)
            val (scopeTraceId, scopeSpanId) = scope.traceContext()
            traceId = scopeTraceId
            spanId = scopeSpanId
        }

        if (traceId == null) {
            return chain.proceed(request)
        }
        if (spanId == null) {
            // traceparent requires a parent id; mint one for this hop
            spanId = TraceContext.generateSpanId()
        }

        return chain.proceed(
            request.newBuilder()
                .header(
                    TraceContext.TRACEPARENT_HEADER,
                    TraceContext.formatTraceparent(traceId, spanId),
                )
                .build()
        )
    }
}

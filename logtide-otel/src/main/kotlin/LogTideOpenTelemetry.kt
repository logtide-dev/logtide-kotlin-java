package dev.logtide.sdk.otel

import dev.logtide.sdk.ActiveTraceContext
import dev.logtide.sdk.Dsn
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler

/**
 * OpenTelemetry tracing preset for LogTide (OTel-native path).
 *
 * One call configures the official OpenTelemetry SDK to export spans to a
 * LogTide instance, and wires the active span's trace context into every log
 * entry captured by `LogTideClient` (log/trace correlation):
 *
 * ```kotlin
 * val provider = LogTideOpenTelemetry.create(
 *     dsn = "https://lp_key@logs.example.com",
 *     service = "checkout",
 *     environment = "production",
 * )
 * val tracer = provider.get("my-app")
 * ```
 *
 * Call `provider.shutdown()` on application exit.
 */
object LogTideOpenTelemetry {

    private val SERVICE_NAME: AttributeKey<String> = AttributeKey.stringKey("service.name")
    private val DEPLOYMENT_ENVIRONMENT: AttributeKey<String> =
        AttributeKey.stringKey("deployment.environment")
    private val SERVICE_VERSION: AttributeKey<String> = AttributeKey.stringKey("service.version")

    fun create(
        apiUrl: String? = null,
        apiKey: String? = null,
        dsn: String? = null,
        service: String,
        environment: String? = null,
        release: String? = null,
        tracesSampleRate: Double = 1.0,
    ): SdkTracerProvider {
        var url = apiUrl
        var key = apiKey
        if (dsn != null) {
            val parts = Dsn.parse(dsn)
            url = url ?: parts.apiUrl
            key = key ?: parts.apiKey
        }
        require(!url.isNullOrBlank() && !key.isNullOrBlank()) {
            "Either dsn or apiUrl + apiKey must be provided"
        }
        require(tracesSampleRate in 0.0..1.0) {
            "tracesSampleRate must be between 0.0 and 1.0"
        }

        val attributes = Attributes.builder().put(SERVICE_NAME, service)
        environment?.let { attributes.put(DEPLOYMENT_ENVIRONMENT, it) }
        release?.let { attributes.put(SERVICE_VERSION, it) }

        val exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint("${url.trimEnd('/')}/v1/otlp/traces")
            .addHeader("X-API-Key", key)
            .build()

        val provider = SdkTracerProvider.builder()
            .setResource(Resource.getDefault().merge(Resource.create(attributes.build())))
            .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(tracesSampleRate)))
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .build()

        // Log/trace correlation: entries captured inside an active span carry
        // its trace context (resolution: explicit -> active span -> scope).
        ActiveTraceContext.provider = {
            val context = Span.current().spanContext
            if (context.isValid) context.traceId to context.spanId else null
        }

        return provider
    }
}

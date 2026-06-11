import dev.logtide.sdk.ActiveTraceContext
import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.models.LogTideClientOptions
import dev.logtide.sdk.otel.LogTideOpenTelemetry
import io.opentelemetry.sdk.trace.SdkTracerProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LogTideOpenTelemetryTest {

    private lateinit var otlpServer: MockWebServer
    private var provider: SdkTracerProvider? = null

    @BeforeEach
    fun setup() {
        otlpServer = MockWebServer()
        otlpServer.start()
    }

    @AfterEach
    fun teardown() {
        ActiveTraceContext.provider = null
        provider?.shutdown()
        otlpServer.shutdown()
    }

    private fun createProvider(sampleRate: Double = 1.0): SdkTracerProvider {
        return LogTideOpenTelemetry.create(
            apiUrl = otlpServer.url("/").toString().removeSuffix("/"),
            apiKey = "lp_test",
            service = "otel-test",
            environment = "staging",
            release = "1.2.3",
            tracesSampleRate = sampleRate,
        ).also { provider = it }
    }

    @Test
    fun `exports spans to the logtide otlp endpoint`() {
        otlpServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val tracer = createProvider().get("test")

        tracer.spanBuilder("checkout").startSpan().end()
        provider!!.forceFlush().join(10, TimeUnit.SECONDS)

        val request = otlpServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/v1/otlp/traces", request.path)
        assertEquals("lp_test", request.getHeader("X-API-Key"))
    }

    @Test
    fun `resource carries the service identity`() {
        // service.name / deployment.environment / service.version on the wire
        otlpServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val tracer = createProvider().get("test")
        tracer.spanBuilder("op").startSpan().end()
        provider!!.forceFlush().join(10, TimeUnit.SECONDS)

        val body = otlpServer.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains("otel-test"), "service.name missing from export")
        assertTrue(body.contains("staging"), "deployment.environment missing from export")
        assertTrue(body.contains("1.2.3"), "service.version missing from export")
    }

    @Test
    fun `sample rate zero records nothing`() {
        val tracer = createProvider(sampleRate = 0.0).get("test")
        repeat(10) { tracer.spanBuilder("unsampled").startSpan().end() }
        provider!!.forceFlush().join(10, TimeUnit.SECONDS)

        assertEquals(0, otlpServer.requestCount)
    }

    @Test
    fun `logs inside a span carry its trace context`() {
        val tracer = createProvider().get("test")
        val ingestServer = MockWebServer().also { it.start() }
        val client = LogTideClient(
            LogTideClientOptions(
                apiUrl = ingestServer.url("/").toString().removeSuffix("/"),
                apiKey = "lp_k",
                flushInterval = 60.seconds,
                service = "svc",
            )
        )
        try {
            val span = tracer.spanBuilder("checkout").startSpan()
            val scope = span.makeCurrent()
            client.info("inside span")
            scope.close()
            span.end()

            ingestServer.enqueue(MockResponse().setResponseCode(200))
            runBlocking { client.flush() }
            val body = ingestServer.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
            assertTrue(
                body.contains("\"trace_id\":\"${span.spanContext.traceId}\""),
                "expected trace id ${span.spanContext.traceId} in: $body",
            )
            assertTrue(
                body.contains("\"span_id\":\"${span.spanContext.spanId}\""),
                "expected span id in body",
            )
        } finally {
            runBlocking { runCatching { client.close() } }
            ingestServer.shutdown()
        }
    }

    @Test
    fun `validates the sample rate`() {
        assertThrows<IllegalArgumentException> {
            LogTideOpenTelemetry.create(apiUrl = "http://h", apiKey = "k", service = "s", tracesSampleRate = 1.5)
        }
    }

    @Test
    fun `accepts a dsn`() {
        val p = LogTideOpenTelemetry.create(dsn = "https://lp_abc@logs.example.com", service = "s")
        assertNotNull(p)
        p.shutdown()
    }
}

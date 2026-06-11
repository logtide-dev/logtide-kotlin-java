package dev.logtide.sdk

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Outbound traceparent injection (conformance C25, spec 005 §2). */
class TraceparentInterceptorTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .addInterceptor(TraceparentInterceptor())
        .build()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))
    }

    @AfterEach
    fun teardown() {
        ActiveTraceContext.provider = null
        ScopeContext.clear()
        server.shutdown()
    }

    private fun call(builder: Request.Builder.() -> Unit = {}) {
        val request = Request.Builder().url(server.url("/")).apply(builder).build()
        client.newCall(request).execute().close()
    }

    private fun received(): String? =
        server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("traceparent")

    @Test
    fun `injects from the current scope`() {
        withScope { scope ->
            scope.setTraceContext("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7")
            call()
        }
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", received())
    }

    @Test
    fun `generates a span id when the scope has none`() {
        withScope { scope ->
            scope.setTraceContext("4bf92f3577b34da6a3ce929d0e0e4736")
            call()
        }
        val header = received()
        assertNotNull(header)
        val parsed = TraceContext.parseTraceparent(header)
        assertNotNull(parsed)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", parsed.traceId)
    }

    @Test
    fun `prefers the active span over the scope`() {
        ActiveTraceContext.provider = { "c".repeat(32) to "d".repeat(16) }
        withScope { scope ->
            scope.setTraceContext("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7")
            call()
        }
        assertEquals("00-${"c".repeat(32)}-${"d".repeat(16)}-01", received())
    }

    @Test
    fun `noop without trace context`() {
        call()
        assertNull(received())
    }

    @Test
    fun `does not override an existing header`() {
        withScope { scope ->
            scope.setTraceContext("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7")
            call { header("traceparent", "00-${"a".repeat(32)}-${"b".repeat(16)}-01") }
        }
        assertTrue(received()!!.contains("a".repeat(32)))
    }
}

package dev.logtide.sdk

import dev.logtide.sdk.models.LogEntry
import dev.logtide.sdk.models.LogTideClientOptions
import dev.logtide.sdk.enums.LogLevel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ScopeUnitTest {

    @AfterEach
    fun cleanup() = ScopeContext.clear()

    @Test
    fun `apply merges tags user breadcrumbs and session into the entry`() {
        val scope = Scope()
        scope.setTag("region", "eu-west-1")
        scope.setExtra("order_id", "42")
        scope.setUser(LogTideUser(id = "u_1", username = "alice"))
        scope.setSessionId("123e4567-e89b-42d3-a456-426614174000")
        scope.addBreadcrumb(Breadcrumb(message = "GET /cart", type = "http"))

        val entry = scope.applyToEntry(LogEntry("svc", LogLevel.INFO, "hello"))

        val md = entry.metadata!!
        assertEquals(mapOf("region" to "eu-west-1"), md["tags"])
        assertEquals("42", md["order_id"])
        assertEquals(mapOf("id" to "u_1", "username" to "alice"), md["user"])
        @Suppress("UNCHECKED_CAST")
        val crumbs = md["breadcrumbs"] as List<Map<String, Any>>
        assertEquals("GET /cart", crumbs[0]["message"])
        assertEquals("123e4567-e89b-42d3-a456-426614174000", entry.sessionId)
    }

    @Test
    fun `entry values win over scope values`() {
        val scope = Scope()
        scope.setExtra("k", "from-scope")
        scope.setTraceContext("a".repeat(32), "b".repeat(16))

        val entry = scope.applyToEntry(
            LogEntry("svc", LogLevel.INFO, "hello", metadata = mapOf("k" to "from-entry"), traceId = "c".repeat(32))
        )
        assertEquals("from-entry", entry.metadata!!["k"])
        assertEquals("c".repeat(32), entry.traceId)
        assertEquals("b".repeat(16), entry.spanId)
    }

    @Test
    fun `breadcrumb ring buffer evicts oldest`() {
        val scope = Scope(maxBreadcrumbs = 3)
        repeat(5) { scope.addBreadcrumb(Breadcrumb(message = "m$it")) }
        val entry = scope.applyToEntry(LogEntry("svc", LogLevel.INFO, "x"))
        @Suppress("UNCHECKED_CAST")
        val crumbs = entry.metadata!!["breadcrumbs"] as List<Map<String, Any>>
        assertEquals(listOf("m2", "m3", "m4"), crumbs.map { it["message"] })
    }

    @Test
    fun `withScope isolates and restores`() {
        ScopeContext.current().setTag("outer", "yes")
        withScope { inner ->
            inner.setTag("inner", "yes")
            val md = inner.applyToEntry(LogEntry("svc", LogLevel.INFO, "x")).metadata!!
            assertEquals(mapOf("outer" to "yes", "inner" to "yes"), md["tags"])
        }
        val md = ScopeContext.current().applyToEntry(LogEntry("svc", LogLevel.INFO, "x")).metadata!!
        assertEquals(mapOf("outer" to "yes"), md["tags"])
    }

    @Test
    fun `withScopeSuspend follows the coroutine across dispatchers`() = runBlocking {
        withScopeSuspend { scope ->
            scope.setTag("task", "a")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val md = ScopeContext.current()
                    .applyToEntry(LogEntry("svc", LogLevel.INFO, "x")).metadata!!
                assertEquals(mapOf("task" to "a"), md["tags"])
            }
        }
    }

    @Test
    fun `scopes are isolated across threads`() {
        ScopeContext.current().setTag("main", "yes")
        var otherThreadTags: Any? = "unset"
        thread {
            otherThreadTags = ScopeContext.current()
                .applyToEntry(LogEntry("svc", LogLevel.INFO, "x")).metadata?.get("tags")
        }.join()
        assertNull(otherThreadTags, "scope must not leak across threads")
    }
}

class ScopeClientIntegrationTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: LogTideClient

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        client = LogTideClient(
            LogTideClientOptions(
                apiUrl = mockServer.url("/").toString().removeSuffix("/"),
                apiKey = "test_key",
                flushInterval = 60.seconds,
                service = "scope-test",
            )
        )
    }

    @AfterEach
    fun teardown() {
        ScopeContext.clear()
        runBlocking { runCatching { client.close() } }
        mockServer.shutdown()
    }

    @Test
    fun `captured entries carry scope state on the wire`() {
        withScope { scope ->
            scope.setTag("region", "eu")
            scope.setUser(LogTideUser(id = "u_7"))
            scope.setSessionId("123e4567-e89b-42d3-a456-426614174000")
            scope.addBreadcrumb(Breadcrumb(message = "clicked buy", type = "ui"))
            client.error("boom")
        }

        mockServer.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.flush() }
        val request = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"tags\":{\"region\":\"eu\"}"), body)
        assertTrue(body.contains("\"user\":{\"id\":\"u_7\"}"), body)
        assertTrue(body.contains("\"clicked buy\""), body)
        assertTrue(body.contains("\"session_id\":\"123e4567-e89b-42d3-a456-426614174000\""), body)
    }

    @Test
    fun `entries outside any scope are unaffected`() {
        client.info("plain")
        mockServer.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.flush() }
        val body = mockServer.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
        assertFalse(body.contains("breadcrumbs"), body)
        assertFalse(body.contains("\"user\""), body)
    }
}

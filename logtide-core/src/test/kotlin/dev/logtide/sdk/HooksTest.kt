package dev.logtide.sdk

import dev.logtide.sdk.models.LogTideClientOptions
import dev.logtide.sdk.models.LogTideClientOptionsBuilder
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

/** beforeSend hook and sampling (conformance C22/C23). */
class HooksTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: LogTideClient

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun teardown() {
        runBlocking { runCatching { client.close() } }
        mockServer.shutdown()
    }

    private fun createClient(configure: LogTideClientOptionsBuilder.() -> Unit): LogTideClient {
        return LogTideClient(
            LogTideClientOptionsBuilder().apply {
                apiUrl = mockServer.url("/").toString().removeSuffix("/")
                apiKey = "test_key"
                flushInterval = 60.seconds
                service = "hooks-test"
                configure()
            }.build()
        )
    }

    private fun flushAndCount(): Int {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.flush() }
        val request = mockServer.takeRequest(2, TimeUnit.SECONDS) ?: return 0
        return Regex("\"message\"").findAll(request.body.readUtf8()).count()
    }

    @Test
    fun `beforeSend can mutate entries`() {
        client = createClient {
            beforeSend = { entry ->
                entry.copy(metadata = (entry.metadata ?: emptyMap()) + ("password" to "[redacted]"))
            }
        }
        client.info("login", mapOf("password" to "hunter2"))

        mockServer.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.flush() }
        val body = mockServer.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains("\"password\":\"[redacted]\""), body)
    }

    @Test
    fun `beforeSend can drop entries`() {
        client = createClient { beforeSend = { null } }
        client.info("dropped")
        assertEquals(0, flushAndCount())
    }

    @Test
    fun `a raising beforeSend keeps the entry`() {
        client = createClient { beforeSend = { error("hook bug") } }
        client.info("survives")
        assertEquals(1, flushAndCount())
    }

    @Test
    fun `sample rate zero sends nothing`() {
        client = createClient { sampleRate = 0.0 }
        repeat(20) { client.info("nope") }
        assertEquals(0, flushAndCount())
    }

    @Test
    fun `sample rate is validated`() {
        assertThrows<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://h", apiKey = "k", sampleRate = 1.5)
        }
    }
}

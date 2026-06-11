package dev.logtide.sdk

import dev.logtide.sdk.exceptions.DsnParseException
import dev.logtide.sdk.models.LogTideClientOptions
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

class DsnTest {

    @Test
    fun `parses basic dsn`() {
        val parts = Dsn.parse("https://lp_abc123@logs.example.com")
        assertEquals("https://logs.example.com", parts.apiUrl)
        assertEquals("lp_abc123", parts.apiKey)
    }

    @Test
    fun `preserves base path and port`() {
        val parts = Dsn.parse("http://lp_k@localhost:8080/logtide")
        assertEquals("http://localhost:8080/logtide", parts.apiUrl)
        assertEquals("lp_k", parts.apiKey)
    }

    @Test
    fun `rejects malformed dsn values`() {
        listOf(
            "",
            "not-a-dsn",
            "ftp://lp_k@host",
            "https://logs.example.com", // no key
            "https://@logs.example.com", // empty key
        ).forEach { dsn ->
            assertThrows<DsnParseException>("should reject: $dsn") { Dsn.parse(dsn) }
        }
    }

    @Test
    fun `options can be built from a dsn`() {
        val options = LogTideClientOptions.fromDsn("https://lp_abc@logs.example.com") {
            batchSize = 5
        }
        assertEquals("https://logs.example.com", options.apiUrl)
        assertEquals("lp_abc", options.apiKey)
        assertEquals(5, options.batchSize)
    }
}

class ServiceInOptionsTest {

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

    private fun createClient(service: String? = "checkout"): LogTideClient {
        return LogTideClient(
            LogTideClientOptions(
                apiUrl = mockServer.url("/").toString().removeSuffix("/"),
                apiKey = "test_key",
                flushInterval = 60.seconds,
                service = service,
            )
        )
    }

    private fun flushAndReadBody(): String {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.flush() }
        val request = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        return request.body.readUtf8()
    }

    @Test
    fun `message-only call uses the configured service`() {
        client = createClient()
        client.info("user logged in")

        val body = flushAndReadBody()
        assertTrue(body.contains("\"service\":\"checkout\""), body)
        assertTrue(body.contains("\"message\":\"user logged in\""), body)
    }

    @Test
    fun `message with metadata uses the configured service`() {
        client = createClient()
        client.error("boom", mapOf("order_id" to "42"))

        val body = flushAndReadBody()
        assertTrue(body.contains("\"service\":\"checkout\""), body)
        assertTrue(body.contains("\"order_id\":\"42\""), body)
    }

    @Test
    fun `message with exception uses the configured service`() {
        client = createClient()
        client.error("failed", RuntimeException("kaboom"))

        val body = flushAndReadBody()
        assertTrue(body.contains("\"service\":\"checkout\""), body)
        assertTrue(body.contains("\"exception\""), body)
    }

    @Test
    fun `legacy two-argument form keeps working`() {
        client = createClient()
        client.info("payments", "captured")

        val body = flushAndReadBody()
        assertTrue(body.contains("\"service\":\"payments\""), body)
        assertTrue(body.contains("\"message\":\"captured\""), body)
    }

    @Test
    fun `message-only call without configured service throws`() {
        client = createClient(service = null)
        assertThrows<IllegalStateException> { client.info("orphan message") }
    }

    @Test
    fun `entries carry sdk metadata`() {
        client = createClient()
        client.info("hello")

        val body = flushAndReadBody()
        assertTrue(body.contains("\"sdk\":{"), body)
        assertTrue(body.contains("\"name\":\"logtide-kotlin\""), body)
        assertTrue(Regex("\"version\":\"\\d+\\.\\d+\\.\\d+\"").containsMatchIn(body), body)
    }
}

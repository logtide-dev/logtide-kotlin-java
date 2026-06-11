import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.LogbackMDCAdapter
import dev.logtide.sdk.LogTideClient
import dev.logtide.sdk.logback.LogTideAppender
import dev.logtide.sdk.models.LogTideClientOptions
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the Logback appender. A standalone LoggerContext is used so the
 * test does not depend on which SLF4J provider wins the classpath binding.
 */
class LogTideAppenderTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: LogTideClient
    private lateinit var context: LoggerContext
    private lateinit var appender: LogTideAppender

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        client = LogTideClient(
            LogTideClientOptions(
                apiUrl = mockServer.url("/").toString().removeSuffix("/"),
                apiKey = "test_key",
                batchSize = 100,
                flushInterval = 60.seconds,
                debug = false,
            )
        )
        context = LoggerContext().apply { mdcAdapter = LogbackMDCAdapter() }
        appender = LogTideAppender().apply {
            this.context = this@LogTideAppenderTest.context
            this.client = this@LogTideAppenderTest.client
            this.serviceName = "logback-test"
            start()
        }
    }

    @AfterEach
    fun teardown() {
        context.mdcAdapter?.clear()
        appender.stop()
        runBlocking { runCatching { client.close() } }
        mockServer.shutdown()
        context.stop()
    }

    private fun logger(name: String = "test.Logger") =
        context.getLogger(name).apply { addAppender(appender) }

    private fun flushAndReadBody(): String {
        mockServer.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.flush() }
        val request = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        return request.body.readUtf8()
    }

    @Test
    fun `appends log events through the client`() {
        logger().info("user logged in")

        val body = flushAndReadBody()
        assertTrue(body.contains("\"message\":\"user logged in\""), body)
        assertTrue(body.contains("\"level\":\"info\""), body)
        assertTrue(body.contains("\"service\":\"logback-test\""), body)
        assertTrue(body.contains("\"logger\":\"test.Logger\""), body)
    }

    @Test
    fun `maps logback levels to logtide levels`() {
        val log = logger().apply { level = Level.TRACE }
        log.trace("t")
        log.debug("d")
        log.info("i")
        log.warn("w")
        log.error("e")

        val body = flushAndReadBody()
        // TRACE and DEBUG both map to debug
        assertEquals(2, Regex("\"level\":\"debug\"").findAll(body).count(), body)
        assertTrue(body.contains("\"level\":\"info\""), body)
        assertTrue(body.contains("\"level\":\"warn\""), body)
        assertTrue(body.contains("\"level\":\"error\""), body)
    }

    @Test
    fun `serializes throwables as canonical exceptions`() {
        logger().error("query failed", RuntimeException("db timeout", IllegalStateException("root")))

        val body = flushAndReadBody()
        assertTrue(body.contains("\"exception\""), body)
        assertTrue(body.contains("\"type\":\"RuntimeException\""), body)
        assertTrue(body.contains("\"language\":\"kotlin\""), body)
        assertTrue(body.contains("\"cause\""), body)
    }

    @Test
    fun `includes mdc values as metadata and trace id top-level`() {
        context.mdcAdapter.put("tenant", "acme")
        context.mdcAdapter.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736")
        logger().info("with mdc")

        val body = flushAndReadBody()
        assertTrue(body.contains("\"tenant\":\"acme\""), body)
        assertTrue(body.contains("\"trace_id\":\"4bf92f3577b34da6a3ce929d0e0e4736\""), body)
    }

    @Test
    fun `does not start without configuration`() {
        val bare = LogTideAppender().apply {
            this.context = this@LogTideAppenderTest.context
            start()
        }
        assertFalse(bare.isStarted)
    }

    @Test
    fun `starts by building its own client from properties`() {
        val own = LogTideAppender().apply {
            this.context = this@LogTideAppenderTest.context
            apiUrl = mockServer.url("/").toString().removeSuffix("/")
            apiKey = "test_key"
            serviceName = "own-client"
            start()
        }
        assertTrue(own.isStarted)
        own.stop()
    }
}

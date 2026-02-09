package dev.logtide.sdk.models

import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for LogTideClientOptions and its validation
 */
class LogTideClientOptionsTest {

    @Test
    fun `should create valid options with default values`() {
        val options = LogTideClientOptions(
            apiUrl = "http://localhost:8080",
            apiKey = "lp_test_key"
        )

        assertEquals("http://localhost:8080", options.apiUrl)
        assertEquals("lp_test_key", options.apiKey)
        assertEquals(100, options.batchSize)
        assertEquals(5.seconds, options.flushInterval)
        assertEquals(10000, options.maxBufferSize)
        assertEquals(3, options.maxRetries)
        assertEquals(1.seconds, options.retryDelay)
        assertEquals(5, options.circuitBreakerThreshold)
        assertEquals(30.seconds, options.circuitBreakerReset)
        assertTrue(options.enableMetrics)
        assertFalse(options.debug)
        assertTrue(options.globalMetadata.isEmpty())
        assertFalse(options.autoTraceId)
    }

    @Test
    fun `should create options with custom values`() {
        val metadata = mapOf("env" to "production", "version" to "1.0")
        val options = LogTideClientOptions(
            apiUrl = "https://api.logtide.dev",
            apiKey = "lp_prod_key",
            batchSize = 50,
            flushInterval = 10.seconds,
            maxBufferSize = 5000,
            maxRetries = 5,
            retryDelay = 2.seconds,
            circuitBreakerThreshold = 10,
            circuitBreakerReset = 60.seconds,
            enableMetrics = false,
            debug = true,
            globalMetadata = metadata,
            autoTraceId = true
        )

        assertEquals("https://api.logtide.dev", options.apiUrl)
        assertEquals("lp_prod_key", options.apiKey)
        assertEquals(50, options.batchSize)
        assertEquals(10.seconds, options.flushInterval)
        assertEquals(5000, options.maxBufferSize)
        assertEquals(5, options.maxRetries)
        assertEquals(2.seconds, options.retryDelay)
        assertEquals(10, options.circuitBreakerThreshold)
        assertEquals(60.seconds, options.circuitBreakerReset)
        assertFalse(options.enableMetrics)
        assertTrue(options.debug)
        assertEquals(metadata, options.globalMetadata)
        assertTrue(options.autoTraceId)
    }

    @Test
    fun `should throw on blank apiUrl`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "", apiKey = "test_key")
        }
        assertEquals("apiUrl cannot be blank", exception.message)
    }

    @Test
    fun `should throw on blank apiUrl with whitespace`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "   ", apiKey = "test_key")
        }
        assertEquals("apiUrl cannot be blank", exception.message)
    }

    @Test
    fun `should throw on blank apiKey`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://localhost", apiKey = "")
        }
        assertEquals("apiKey cannot be blank", exception.message)
    }

    @Test
    fun `should throw on zero batchSize`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key", batchSize = 0)
        }
        assertEquals("batchSize must be positive", exception.message)
    }

    @Test
    fun `should throw on negative batchSize`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key", batchSize = -1)
        }
        assertEquals("batchSize must be positive", exception.message)
    }

    @Test
    fun `should throw on zero maxBufferSize`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key", maxBufferSize = 0)
        }
        assertEquals("maxBufferSize must be positive", exception.message)
    }

    @Test
    fun `should throw on negative maxBufferSize`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key", maxBufferSize = -100)
        }
        assertEquals("maxBufferSize must be positive", exception.message)
    }

    @Test
    fun `should throw on negative maxRetries`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key", maxRetries = -1)
        }
        assertEquals("maxRetries must be non-negative", exception.message)
    }

    @Test
    fun `should allow zero maxRetries`() {
        val options = LogTideClientOptions(
            apiUrl = "http://localhost",
            apiKey = "key",
            maxRetries = 0
        )
        assertEquals(0, options.maxRetries)
    }

    @Test
    fun `should throw on zero circuitBreakerThreshold`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(
                apiUrl = "http://localhost",
                apiKey = "key",
                circuitBreakerThreshold = 0
            )
        }
        assertEquals("circuitBreakerThreshold must be positive", exception.message)
    }

    @Test
    fun `should throw on negative circuitBreakerThreshold`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LogTideClientOptions(
                apiUrl = "http://localhost",
                apiKey = "key",
                circuitBreakerThreshold = -5
            )
        }
        assertEquals("circuitBreakerThreshold must be positive", exception.message)
    }

    // ==================== Deprecated Properties Tests ====================

    @Suppress("DEPRECATION")
    @Test
    fun `should return flushIntervalMs from flushInterval`() {
        val options = LogTideClientOptions(
            apiUrl = "http://localhost",
            apiKey = "key",
            flushInterval = 5.seconds
        )
        assertEquals(5000L, options.flushIntervalMs)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `should return retryDelayMs from retryDelay`() {
        val options = LogTideClientOptions(
            apiUrl = "http://localhost",
            apiKey = "key",
            retryDelay = 2.seconds
        )
        assertEquals(2000L, options.retryDelayMs)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `should return circuitBreakerResetMs from circuitBreakerReset`() {
        val options = LogTideClientOptions(
            apiUrl = "http://localhost",
            apiKey = "key",
            circuitBreakerReset = 30.seconds
        )
        assertEquals(30000L, options.circuitBreakerResetMs)
    }

    // ==================== DSL Builder Tests ====================

    @Test
    fun `should build options using DSL builder`() {
        val options = logTideClient {
            apiUrl = "http://localhost:8080"
            apiKey = "lp_test_key"
            batchSize = 200
            debug = true
        }

        assertEquals("http://localhost:8080", options.apiUrl)
        assertEquals("lp_test_key", options.apiKey)
        assertEquals(200, options.batchSize)
        assertTrue(options.debug)
    }

    @Test
    fun `should throw validation error from DSL builder`() {
        assertFailsWith<IllegalArgumentException> {
            logTideClient {
                apiUrl = ""
                apiKey = "key"
            }
        }
    }

    @Test
    fun `should build options with DSL global metadata`() {
        val options = logTideClient {
            apiUrl = "http://localhost"
            apiKey = "key"
            globalMetadata = mapOf("service" to "api", "region" to "eu-west-1")
        }

        assertEquals(2, options.globalMetadata.size)
        assertEquals("api", options.globalMetadata["service"])
        assertEquals("eu-west-1", options.globalMetadata["region"])
    }

    @Test
    fun `DSL builder should have correct defaults`() {
        val builder = LogTideClientOptionsBuilder()

        assertEquals("", builder.apiUrl)
        assertEquals("", builder.apiKey)
        assertEquals(100, builder.batchSize)
        assertEquals(5.seconds, builder.flushInterval)
        assertEquals(10000, builder.maxBufferSize)
        assertEquals(3, builder.maxRetries)
        assertEquals(1.seconds, builder.retryDelay)
        assertEquals(5, builder.circuitBreakerThreshold)
        assertEquals(30.seconds, builder.circuitBreakerReset)
        assertTrue(builder.enableMetrics)
        assertFalse(builder.debug)
        assertTrue(builder.globalMetadata.isEmpty())
        assertFalse(builder.autoTraceId)
    }

    // ==================== Data Class Tests ====================

    @Test
    fun `options should support equals and hashCode`() {
        val options1 = LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key")
        val options2 = LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key")
        val options3 = LogTideClientOptions(apiUrl = "http://localhost", apiKey = "different")

        assertEquals(options1, options2)
        assertEquals(options1.hashCode(), options2.hashCode())
        assertNotEquals(options1, options3)
    }

    @Test
    fun `options should support copy`() {
        val original = LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key")
        val copied = original.copy(debug = true)

        assertEquals("http://localhost", copied.apiUrl)
        assertEquals("key", copied.apiKey)
        assertTrue(copied.debug)
        assertFalse(original.debug)
    }

    @Test
    fun `options should have readable toString`() {
        val options = LogTideClientOptions(apiUrl = "http://localhost", apiKey = "key")
        val str = options.toString()

        assertTrue(str.contains("apiUrl=http://localhost"))
        assertTrue(str.contains("apiKey=key"))
        assertTrue(str.contains("batchSize=100"))
    }
}

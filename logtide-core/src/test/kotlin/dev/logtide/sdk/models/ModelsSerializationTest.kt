package dev.logtide.sdk.models

import dev.logtide.sdk.enums.LogLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for models serialization
 */
class ModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ==================== LogEntry Tests ====================

    @Test
    fun `LogEntry should serialize with all fields`() {
        val entry = LogEntry(
            service = "test-service",
            level = LogLevel.INFO,
            message = "Test message",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("key" to "value"),
            traceId = "550e8400-e29b-41d4-a716-446655440000"
        )

        val jsonString = json.encodeToString(entry)

        assertTrue(jsonString.contains("\"service\":\"test-service\""))
        assertTrue(jsonString.contains("\"level\":\"info\""))  // lowercase
        assertTrue(jsonString.contains("\"message\":\"Test message\""))
        assertTrue(jsonString.contains("\"time\":\"2024-01-01T12:00:00Z\""))
        assertTrue(jsonString.contains("\"key\":\"value\""))
        assertTrue(jsonString.contains("\"trace_id\":\"550e8400-e29b-41d4-a716-446655440000\""))
    }

    @Test
    fun `LogEntry should serialize with null optional fields`() {
        val entry = LogEntry(
            service = "test-service",
            level = LogLevel.ERROR,
            message = "Error message",
            time = "2024-01-01T12:00:00Z"
        )

        val jsonString = json.encodeToString(entry)

        assertTrue(jsonString.contains("\"service\":\"test-service\""))
        assertTrue(jsonString.contains("\"level\":\"error\""))  // lowercase
        assertTrue(jsonString.contains("\"message\":\"Error message\""))
    }

    @Test
    fun `LogEntry should deserialize from JSON`() {
        val jsonString = """
            {
                "service": "api-service",
                "level": "warn",
                "message": "Warning message",
                "time": "2024-01-01T15:30:00Z",
                "trace_id": "123-456-789"
            }
        """.trimIndent()

        val entry = json.decodeFromString<LogEntry>(jsonString)

        assertEquals("api-service", entry.service)
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("Warning message", entry.message)
        assertEquals("2024-01-01T15:30:00Z", entry.time)
        assertEquals("123-456-789", entry.traceId)
        assertNull(entry.metadata)
    }

    @Test
    fun `LogEntry should serialize all log levels`() {
        LogLevel.entries.forEach { level ->
            val entry = LogEntry(
                service = "test",
                level = level,
                message = "Message with ${level.name}",
                time = "2024-01-01T12:00:00Z"
            )

            val jsonString = json.encodeToString(entry)
            assertTrue(jsonString.contains("\"level\":\"${level.value}\""))  // Uses lowercase value
        }
    }

    // ==================== AnyValueSerializer Tests ====================

    @Test
    fun `AnyValueSerializer should serialize String`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("string" to "hello")
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"string\":\"hello\""))
    }

    @Test
    fun `AnyValueSerializer should serialize Int`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("number" to 42)
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"number\":42"))
    }

    @Test
    fun `AnyValueSerializer should serialize Long`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("bigNumber" to 9999999999L)
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"bigNumber\":9999999999"))
    }

    @Test
    fun `AnyValueSerializer should serialize Double`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("decimal" to 3.14159)
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"decimal\":3.14159"))
    }

    @Test
    fun `AnyValueSerializer should serialize Boolean`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("enabled" to true, "disabled" to false)
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"enabled\":true"))
        assertTrue(jsonString.contains("\"disabled\":false"))
    }

    @Test
    fun `AnyValueSerializer should serialize List`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("items" to listOf("a", "b", "c"))
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"items\":[\"a\",\"b\",\"c\"]"))
    }

    @Test
    fun `AnyValueSerializer should serialize List with mixed types`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("mixed" to listOf("string", 42, true))
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("[\"string\",42,true]"))
    }

    @Test
    fun `AnyValueSerializer should serialize nested Map`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf(
                "nested" to mapOf(
                    "level1" to mapOf(
                        "level2" to "deep value"
                    )
                )
            )
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("\"nested\""))
        assertTrue(jsonString.contains("\"level1\""))
        assertTrue(jsonString.contains("\"level2\""))
        assertTrue(jsonString.contains("\"deep value\""))
    }

    @Test
    fun `AnyValueSerializer should serialize complex nested structure`() {
        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf(
                "user" to mapOf(
                    "id" to 123,
                    "name" to "John",
                    "roles" to listOf("admin", "user"),
                    "settings" to mapOf(
                        "notifications" to true,
                        "theme" to "dark"
                    )
                )
            )
        )

        val jsonString = json.encodeToString(entry)

        val parsed = json.parseToJsonElement(jsonString).jsonObject
        assertNotNull(parsed["metadata"])
    }

    @Test
    fun `AnyValueSerializer should handle unknown types via toString`() {
        // Custom object that's not a primitive, Map, or List
        data class CustomObject(val value: String) {
            override fun toString() = "CustomObject($value)"
        }

        val entry = LogEntry(
            service = "test",
            level = LogLevel.INFO,
            message = "msg",
            time = "2024-01-01T12:00:00Z",
            metadata = mapOf("custom" to CustomObject("test"))
        )

        val jsonString = json.encodeToString(entry)
        assertTrue(jsonString.contains("CustomObject(test)"))
    }

    @Test
    fun `AnyValueSerializer should deserialize primitive types`() {
        val jsonString = """
            {
                "service": "test",
                "level": "info",
                "message": "msg",
                "time": "2024-01-01T12:00:00Z",
                "metadata": {
                    "string": "hello",
                    "number": 42,
                    "decimal": 3.14,
                    "bool": true
                }
            }
        """.trimIndent()

        val entry = json.decodeFromString<LogEntry>(jsonString)

        assertNotNull(entry.metadata)
        assertEquals("hello", entry.metadata!!["string"])
        assertEquals(42L, entry.metadata!!["number"]) // JSON numbers deserialize as Long
        assertEquals(3.14, entry.metadata!!["decimal"])
        assertEquals(true, entry.metadata!!["bool"])
    }

    @Test
    fun `AnyValueSerializer should deserialize nested structures`() {
        val jsonString = """
            {
                "service": "test",
                "level": "info",
                "message": "msg",
                "time": "2024-01-01T12:00:00Z",
                "metadata": {
                    "nested": {
                        "key": "value"
                    },
                    "list": [1, 2, 3]
                }
            }
        """.trimIndent()

        val entry = json.decodeFromString<LogEntry>(jsonString)

        assertNotNull(entry.metadata)
        @Suppress("UNCHECKED_CAST")
        val nested = entry.metadata!!["nested"] as Map<String, Any>
        assertEquals("value", nested["key"])

        @Suppress("UNCHECKED_CAST")
        val list = entry.metadata!!["list"] as List<Any>
        assertEquals(3, list.size)
    }

    // ==================== LogsResponse Tests ====================

    @Test
    fun `LogsResponse should serialize correctly`() {
        val response = LogsResponse(
            logs = listOf(
                LogEntry("svc1", LogLevel.INFO, "msg1", "2024-01-01T12:00:00Z"),
                LogEntry("svc2", LogLevel.ERROR, "msg2", "2024-01-01T12:01:00Z")
            ),
            total = 100,
            limit = 10,
            offset = 0
        )

        val jsonString = json.encodeToString(response)

        assertTrue(jsonString.contains("\"total\":100"))
        assertTrue(jsonString.contains("\"limit\":10"))
        assertTrue(jsonString.contains("\"offset\":0"))
        assertTrue(jsonString.contains("\"logs\""))
    }

    @Test
    fun `LogsResponse should deserialize correctly`() {
        val jsonString = """
            {
                "logs": [
                    {
                        "service": "api",
                        "level": "info",
                        "message": "Request handled",
                        "time": "2024-01-01T12:00:00Z"
                    }
                ],
                "total": 50,
                "limit": 20,
                "offset": 10
            }
        """.trimIndent()

        val response = json.decodeFromString<LogsResponse>(jsonString)

        assertEquals(50, response.total)
        assertEquals(20, response.limit)
        assertEquals(10, response.offset)
        assertEquals(1, response.logs.size)
        assertEquals("api", response.logs[0].service)
        assertEquals(LogLevel.INFO, response.logs[0].level)
    }

    // ==================== ClientMetrics Tests ====================

    @Test
    fun `ClientMetrics should have correct defaults`() {
        val metrics = ClientMetrics()

        assertEquals(0L, metrics.logsSent)
        assertEquals(0L, metrics.logsDropped)
        assertEquals(0L, metrics.errors)
        assertEquals(0L, metrics.retries)
        assertEquals(0.0, metrics.avgLatencyMs)
        assertEquals(0L, metrics.circuitBreakerTrips)
    }

    @Test
    fun `ClientMetrics should accept custom values`() {
        val metrics = ClientMetrics(
            logsSent = 1000,
            logsDropped = 5,
            errors = 10,
            retries = 20,
            avgLatencyMs = 50.5,
            circuitBreakerTrips = 2
        )

        assertEquals(1000L, metrics.logsSent)
        assertEquals(5L, metrics.logsDropped)
        assertEquals(10L, metrics.errors)
        assertEquals(20L, metrics.retries)
        assertEquals(50.5, metrics.avgLatencyMs)
        assertEquals(2L, metrics.circuitBreakerTrips)
    }

    @Test
    fun `ClientMetrics should support copy`() {
        val original = ClientMetrics(logsSent = 100)
        val copied = original.copy(logsSent = 200)

        assertEquals(100L, original.logsSent)
        assertEquals(200L, copied.logsSent)
    }

    @Test
    fun `ClientMetrics should support equals and hashCode`() {
        val metrics1 = ClientMetrics(logsSent = 100, errors = 5)
        val metrics2 = ClientMetrics(logsSent = 100, errors = 5)
        val metrics3 = ClientMetrics(logsSent = 100, errors = 10)

        assertEquals(metrics1, metrics2)
        assertEquals(metrics1.hashCode(), metrics2.hashCode())
        assertNotEquals(metrics1, metrics3)
    }
}

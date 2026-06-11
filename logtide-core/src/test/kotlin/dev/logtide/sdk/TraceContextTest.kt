package dev.logtide.sdk

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceContextTest {

    private val validTraceId = "4bf92f3577b34da6a3ce929d0e0e4736"
    private val validSpanId = "00f067aa0ba902b7"
    private val validHeader = "00-$validTraceId-$validSpanId-01"

    @Test
    fun `generateTraceId returns 32 lowercase hex chars`() {
        repeat(20) {
            val id = TraceContext.generateTraceId()
            assertTrue(id.matches(Regex("[0-9a-f]{32}")), id)
            assertNotEquals("0".repeat(32), id)
        }
    }

    @Test
    fun `generateSpanId returns 16 lowercase hex chars`() {
        repeat(20) {
            val id = TraceContext.generateSpanId()
            assertTrue(id.matches(Regex("[0-9a-f]{16}")), id)
            assertNotEquals("0".repeat(16), id)
        }
    }

    @Test
    fun `parses valid traceparent`() {
        val ctx = TraceContext.parseTraceparent(validHeader)
        assertEquals(validTraceId, ctx?.traceId)
        assertEquals(validSpanId, ctx?.spanId)
        assertEquals(true, ctx?.sampled)
    }

    @Test
    fun `parses unsampled flag`() {
        val ctx = TraceContext.parseTraceparent("00-$validTraceId-$validSpanId-00")
        assertEquals(false, ctx?.sampled)
    }

    @Test
    fun `rejects malformed traceparent values`() {
        listOf(
            null,
            "",
            "garbage",
            "00-abc-def-01",
            "00-${"0".repeat(32)}-$validSpanId-01", // all-zero trace id
            "00-$validTraceId-${"0".repeat(16)}-01", // all-zero span id
            "00-$validTraceId-$validSpanId", // missing flags
            "ff-$validTraceId-$validSpanId-01", // forbidden version
            "00-${validTraceId.uppercase()}-$validSpanId-01", // uppercase hex
        ).forEach { header ->
            assertNull(TraceContext.parseTraceparent(header), "should reject: $header")
        }
    }

    @Test
    fun `formatTraceparent round-trips`() {
        val header = TraceContext.formatTraceparent(validTraceId, validSpanId, sampled = true)
        assertEquals(validHeader, header)
        assertEquals(validTraceId, TraceContext.parseTraceparent(header)?.traceId)
    }

    @Test
    fun `resolveTraceId prefers traceparent over legacy header`() {
        assertEquals(validTraceId, TraceContext.resolveTraceId(validHeader, "legacy-123"))
    }

    @Test
    fun `resolveTraceId falls back to legacy header`() {
        assertEquals("legacy-123", TraceContext.resolveTraceId(null, "legacy-123"))
        assertEquals("legacy-123", TraceContext.resolveTraceId("garbage", "legacy-123"))
    }

    @Test
    fun `resolveTraceId generates when nothing is present`() {
        val id = TraceContext.resolveTraceId(null, null)
        assertTrue(id.matches(Regex("[0-9a-f]{32}")), id)
    }
}

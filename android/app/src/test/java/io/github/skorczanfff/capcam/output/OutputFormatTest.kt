package io.github.skorczanfff.capcam.output

import io.github.skorczanfff.capcam.tracking.HeadAngles
import io.github.skorczanfff.capcam.tracking.Quat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OutputFormatTest {

    private val sample = Sample(
        head = Quat(0.1, -0.2, 0.3, 0.9),
        angles = HeadAngles(yaw = 12.5, pitch = -3.25, roll = 7.0),
        sequence = 0x01020304,
        timestampNs = 0x0A0B0C0D0E0F1011,
    )

    private fun encode(format: OutputFormat): ByteBuffer {
        val buf = ByteBuffer.allocate(format.packetSize).order(ByteOrder.LITTLE_ENDIAN)
        format.encode(sample, buf)
        assertEquals("bytes written", format.packetSize, buf.position())
        return buf.flip() as ByteBuffer
    }

    @Test
    fun opentrackIsSixLittleEndianDoubles() {
        val b = encode(OpentrackFormat)
        assertEquals(0.0, b.getDouble(0), 0.0)
        assertEquals(0.0, b.getDouble(8), 0.0)
        assertEquals(0.0, b.getDouble(16), 0.0)
        assertEquals(12.5, b.getDouble(24), 0.0)
        assertEquals(-3.25, b.getDouble(32), 0.0)
        assertEquals(7.0, b.getDouble(40), 0.0)
        // Little-endian check on the raw bytes: 12.5 = 0x4029000000000000.
        assertEquals(0x40.toByte(), b.get(31))
        assertEquals(0x29.toByte(), b.get(30))
    }

    @Test
    fun capCamLayoutMatchesTheProtocol() {
        val b = encode(CapCamFormat)
        assertEquals("CCAM", String(ByteArray(4) { b.get(it) }, Charsets.US_ASCII))
        assertEquals(1, b.get(4).toInt())
        assertEquals(0, b.get(5).toInt())
        assertEquals(0, b.getShort(6).toInt())
        assertEquals(0x01020304, b.getInt(8))
        assertEquals(0x04.toByte(), b.get(8))
        assertEquals(0x0A0B0C0D0E0F1011, b.getLong(12))
        assertEquals(0.1f, b.getFloat(20), 0f)
        assertEquals(-0.2f, b.getFloat(24), 0f)
        assertEquals(0.3f, b.getFloat(28), 0f)
        assertEquals(0.9f, b.getFloat(32), 0f)
    }
}

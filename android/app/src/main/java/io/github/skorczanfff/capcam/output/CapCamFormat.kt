package io.github.skorczanfff.capcam.output

import java.nio.ByteBuffer

/** CapCam v1: magic, version, flags, sequence, sensor timestamp and the head → world quaternion. */
object CapCamFormat : OutputFormat {
    override val label = "CapCam"
    override val defaultPort = 4243
    override val packetSize = 36

    private val MAGIC = "CCAM".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1

    /** bit0 = 0: the quaternion comes from the game rotation vector (no magnetometer). */
    private const val FLAGS: Byte = 0

    override fun encode(sample: Sample, out: ByteBuffer) {
        out.put(MAGIC)
        out.put(VERSION)
        out.put(FLAGS)
        out.putShort(0)
        out.putInt(sample.sequence)
        out.putLong(sample.timestampNs)
        out.putFloat(sample.head.x.toFloat())
        out.putFloat(sample.head.y.toFloat())
        out.putFloat(sample.head.z.toFloat())
        out.putFloat(sample.head.w.toFloat())
    }
}

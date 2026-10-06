package io.github.skorczanfff.capcam.output

import java.nio.ByteBuffer

/**
 * opentrack "UDP over network" input: six f64 values x, y, z (cm), yaw, pitch, roll (degrees).
 * [io.github.skorczanfff.capcam.tracking.HeadAngles] already uses opentrack's signs (yaw+ = right).
 * The phone can't measure position, so x, y and z are always 0.
 */
object OpentrackFormat : OutputFormat {
    override val label = "opentrack"
    override val defaultPort = 4242
    override val packetSize = 48

    override fun encode(sample: Sample, out: ByteBuffer) {
        out.putDouble(0.0)
        out.putDouble(0.0)
        out.putDouble(0.0)
        out.putDouble(sample.angles.yaw)
        out.putDouble(sample.angles.pitch)
        out.putDouble(sample.angles.roll)
    }
}

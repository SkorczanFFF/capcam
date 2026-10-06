package io.github.skorczanfff.capcam.output

import io.github.skorczanfff.capcam.tracking.HeadAngles
import io.github.skorczanfff.capcam.tracking.Quat
import java.nio.ByteBuffer

/** One tracking sample, already mounted and zeroed. */
class Sample(
    val head: Quat,
    val angles: HeadAngles,
    val sequence: Int,
    val timestampNs: Long,
)

/** Wire format sent to the PC. [encode] writes exactly [packetSize] bytes, little-endian. */
interface OutputFormat {
    val label: String
    val defaultPort: Int
    val packetSize: Int
    fun encode(sample: Sample, out: ByteBuffer)

    companion object {
        val ALL: List<OutputFormat> = listOf(OpentrackFormat, CapCamFormat)
    }
}

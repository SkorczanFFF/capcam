package io.github.skorczanfff.capcam.tracking

/**
 * Applies the mounting preset and zeroes yaw, so "forward" is wherever you looked when
 * tracking started (or at the last [recenter]). Pitch and roll stay relative to gravity.
 *
 * The first sensor events after registering can be stale or not yet fused, so for [settleNs]
 * after the first event the neutral pose is returned and nothing is zeroed.
 * Not thread-safe: call from the sensor thread only.
 */
class HeadTracker(private val mounting: Mounting, private val settleNs: Long = SETTLE_NS) {

    private var yawOffset: Quat? = null
    private var firstTimestampNs: Long? = null

    /** Yaw (degrees, head frame) that was taken as "forward" at the last zeroing; null until then. */
    var zeroYaw: Double? = null
        private set

    fun recenter() {
        yawOffset = null
        zeroYaw = null
    }

    /** Device → world rotation from the sensor in, zeroed head → world rotation out. */
    fun process(deviceToWorld: Quat, timestampNs: Long): Quat {
        val first = firstTimestampNs ?: timestampNs.also { firstTimestampNs = it }
        if (yawOffset == null && timestampNs - first < settleNs) return Quat.IDENTITY

        val head = mounting.headOrientation(deviceToWorld)
        val offset = yawOffset ?: run {
            val yaw = head.toHeadAngles().yaw
            zeroYaw = yaw
            // Yaw+ is a turn to the right, i.e. a negative rotation about up: undo it with a positive one.
            Quat.aboutZ(Math.toRadians(yaw)).also { yawOffset = it }
        }
        return offset * head
    }

    companion object {
        const val SETTLE_NS = 500_000_000L
    }
}

package io.github.skorczanfff.capcam.tracking

/**
 * Applies the mounting preset and zeroes yaw, so "forward" is wherever you looked when
 * tracking started (or at the last [recenter]). Pitch and roll stay relative to gravity.
 * Not thread-safe: call from the sensor thread only.
 */
class HeadTracker(private val mounting: Mounting) {

    private var yawOffset: Quat? = null

    fun recenter() {
        yawOffset = null
    }

    /** Device → world rotation from the sensor in, zeroed head → world rotation out. */
    fun process(deviceToWorld: Quat): Quat {
        val head = mounting.headOrientation(deviceToWorld)
        val offset = yawOffset
            ?: Quat.aboutZ(-Math.toRadians(head.toHeadAngles().yaw)).also { yawOffset = it }
        return offset * head
    }
}

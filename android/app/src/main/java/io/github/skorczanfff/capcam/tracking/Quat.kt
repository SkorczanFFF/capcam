package io.github.skorczanfff.capcam.tracking

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Unit quaternion (x, y, z, w). Rotations compose like matrices: `(a * b)` applies `b` first. */
data class Quat(val x: Double, val y: Double, val z: Double, val w: Double) {

    operator fun times(o: Quat) = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )

    /**
     * Euler angles of a head → world rotation. World: z up. Head: x right, y forward, z up.
     * Order: yaw about world up, then pitch about head right, then roll about head forward
     * (`R = Rz(yaw) · Rx(pitch) · Ry(roll)`).
     *
     * Signs follow the right-hand rule: yaw+ = turn left, pitch+ = look up, roll+ = right ear down.
     */
    fun toHeadAngles(): HeadAngles {
        val m01 = 2 * (x * y - z * w)
        val m11 = 1 - 2 * (x * x + z * z)
        val m20 = 2 * (x * z - y * w)
        val m21 = 2 * (y * z + x * w)
        val m22 = 1 - 2 * (x * x + y * y)
        return HeadAngles(
            yaw = Math.toDegrees(atan2(-m01, m11)),
            pitch = Math.toDegrees(asin(m21.coerceIn(-1.0, 1.0))),
            roll = Math.toDegrees(atan2(-m20, m22)),
        )
    }

    companion object {
        val IDENTITY = Quat(0.0, 0.0, 0.0, 1.0)

        fun aboutX(rad: Double) = Quat(sin(rad / 2), 0.0, 0.0, cos(rad / 2))
        fun aboutY(rad: Double) = Quat(0.0, sin(rad / 2), 0.0, cos(rad / 2))
        fun aboutZ(rad: Double) = Quat(0.0, 0.0, sin(rad / 2), cos(rad / 2))

        /** Quaternion of a proper rotation matrix `m[row][col]` (Shepperd's method). */
        fun fromRotationMatrix(m: Array<DoubleArray>): Quat {
            val trace = m[0][0] + m[1][1] + m[2][2]
            return when {
                trace > 0 -> {
                    val s = sqrt(trace + 1) * 2
                    Quat((m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s, s / 4)
                }
                m[0][0] > m[1][1] && m[0][0] > m[2][2] -> {
                    val s = sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2
                    Quat(s / 4, (m[0][1] + m[1][0]) / s, (m[0][2] + m[2][0]) / s, (m[2][1] - m[1][2]) / s)
                }
                m[1][1] > m[2][2] -> {
                    val s = sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2
                    Quat((m[0][1] + m[1][0]) / s, s / 4, (m[1][2] + m[2][1]) / s, (m[0][2] - m[2][0]) / s)
                }
                else -> {
                    val s = sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2
                    Quat((m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s, s / 4, (m[1][0] - m[0][1]) / s)
                }
            }
        }
    }
}

/** Head angles in degrees. */
data class HeadAngles(val yaw: Double, val pitch: Double, val roll: Double)

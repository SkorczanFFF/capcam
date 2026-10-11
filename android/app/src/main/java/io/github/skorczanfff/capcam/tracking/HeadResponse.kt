package io.github.skorczanfff.capcam.tracking

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * How one head axis reaches the game: off (always 0°), or the head angle times [gain].
 * Past [boostFrom] degrees the sensitivity keeps rising by [boost] every 10°, so the centre stays
 * calm while a small extra head turn still reaches far to the side. The curve has no kink:
 * at [boostFrom] it continues at exactly [gain].
 */
data class AxisResponse(
    val enabled: Boolean = true,
    val gain: Double = 1.0,
    val boost: Double = 0.0,
    val boostFrom: Double = DEFAULT_BOOST_FROM,
) {

    fun apply(degrees: Double): Double {
        if (!enabled) return 0.0
        val past = abs(degrees) - boostFrom
        val extra = if (past > 0) boost * past * past / 20 else 0.0
        return degrees * gain + sign(degrees) * extra
    }

    /** The same axis with [value] as gain, kept in range and rounded to [GAIN_STEP]. */
    fun withGain(value: Double) = copy(gain = snap(value, MIN_GAIN, MAX_GAIN, GAIN_STEP))

    /** The same axis with [value] as boost, kept in range and rounded to [BOOST_STEP]. */
    fun withBoost(value: Double) = copy(boost = snap(value, 0.0, MAX_BOOST, BOOST_STEP))

    /** The same axis with the boost starting at [value] degrees, kept in range and rounded to [BOOST_FROM_STEP]. */
    fun withBoostFrom(value: Double) = copy(boostFrom = snap(value, MIN_BOOST_FROM, MAX_BOOST_FROM, BOOST_FROM_STEP))

    companion object {
        const val MIN_GAIN = 0.5
        const val MAX_GAIN = 3.0
        const val GAIN_STEP = 0.1

        /** Sensitivity added every 10° past [boostFrom]. */
        const val MAX_BOOST = 3.0
        const val BOOST_STEP = 0.1

        const val DEFAULT_BOOST_FROM = 20.0
        const val MIN_BOOST_FROM = 5.0
        const val MAX_BOOST_FROM = 45.0
        const val BOOST_FROM_STEP = 5.0

        private fun snap(value: Double, min: Double, max: Double, step: Double) =
            (value.coerceIn(min, max) / step).roundToInt() * step
    }
}

/**
 * Per-axis on/off, sensitivity and side boost, applied to the zeroed head pose before it is sent.
 * With a yaw gain of 2, turning the head 45° turns the camera 90°.
 * Results are clamped to the Euler ranges (yaw and roll ±180°, pitch ±90°), so a big gain stops
 * at the limit instead of wrapping round.
 */
data class HeadResponse(
    val yaw: AxisResponse = AxisResponse(),
    val pitch: AxisResponse = AxisResponse(),
    val roll: AxisResponse = AxisResponse(),
) {
    fun apply(head: Quat): Quat {
        // Pass the pose through untouched rather than round-tripping it through Euler angles.
        if (this == NATURAL) return head
        val a = head.toHeadAngles()
        return Quat.fromHeadAngles(
            HeadAngles(
                yaw = yaw.apply(a.yaw).coerceIn(-180.0, 180.0),
                pitch = pitch.apply(a.pitch).coerceIn(-90.0, 90.0),
                roll = roll.apply(a.roll).coerceIn(-180.0, 180.0),
            )
        )
    }

    companion object {
        /** Every axis on at 1×: the head pose as measured. */
        val NATURAL = HeadResponse()
    }
}

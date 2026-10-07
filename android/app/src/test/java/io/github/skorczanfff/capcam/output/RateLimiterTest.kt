package io.github.skorczanfff.capcam.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RateLimiterTest {

    /** Sends counted over one second of sensor events every [sensorPeriodNs] (± [jitterNs]). */
    private fun sentPerSecond(hz: Int?, sensorPeriodNs: Long, jitterNs: Long = 0): Int {
        val limiter = RateLimiter(hz)
        val random = Random(42)
        var t = 1_000_000_000L
        var sent = 0
        val end = t + 1_000_000_000L
        while (t < end) {
            if (limiter.shouldSend(t)) sent++
            t += sensorPeriodNs + if (jitterNs > 0) random.nextLong(-jitterNs, jitterNs) else 0
        }
        return sent
    }

    @Test
    fun maxSendsEverything() {
        assertEquals(200, sentPerSecond(null, 5_000_000))
    }

    @Test
    fun capsAFasterSensor() {
        assertEquals(100, sentPerSecond(100, 5_000_000), 1.0)
        assertEquals(60, sentPerSecond(60, 5_000_000), 1.0)
        assertEquals(100, sentPerSecond(100, 2_500_000), 1.0)
    }

    @Test
    fun sensorSlightlyFasterThanRequestedIsNotHalved() {
        // Asked for 100 Hz, sensor delivers 101 Hz: must stay close to 100, not drop to 50.
        assertEquals(100, sentPerSecond(100, 9_900_000), 2.0)
    }

    @Test
    fun sensorAtTheRequestedRateWithJitterKeepsEverySample() {
        val sent = sentPerSecond(100, 10_000_000, jitterNs = 1_500_000)
        assertTrue("sent $sent", sent in 98..101)
    }

    @Test
    fun slowerSensorJustPassesThrough() {
        assertEquals(50, sentPerSecond(100, 20_000_000))
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Double) =
        assertEquals(expected.toDouble(), actual.toDouble(), delta)
}

package io.github.skorczanfff.capcam.output

/** How often packets go out. [hz] = null sends every sensor sample (about 200 Hz on most phones). */
enum class SendRate(val label: String, val hz: Int?) {
    HZ_60("60", 60),
    HZ_100("100", 100),
    MAX("Max", null),
}

/**
 * Lets at most [hz] samples per second through, on average, whatever rate the sensor really
 * delivers. It keeps a schedule of due times instead of checking the gap since the last send:
 * a sensor running slightly faster than requested would otherwise lose every other sample.
 */
class RateLimiter(hz: Int?) {

    private val intervalNs: Long? = hz?.let { 1_000_000_000L / it }
    private var nextDueNs: Long? = null

    /** [timestampNs] must come from one monotonic clock (sensor event timestamps). */
    fun shouldSend(timestampNs: Long): Boolean {
        val interval = intervalNs ?: return true
        val due = nextDueNs ?: timestampNs
        // A quarter interval of slack absorbs jitter around the due time.
        if (timestampNs < due - interval / 4) return false
        // Stay on schedule, but don't try to catch up after a gap (e.g. the sensor paused).
        nextDueNs = if (timestampNs - due > interval) timestampNs + interval else due + interval
        return true
    }
}

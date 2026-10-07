package io.github.skorczanfff.capcam

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.skorczanfff.capcam.output.OutputFormat
import io.github.skorczanfff.capcam.output.RateLimiter
import io.github.skorczanfff.capcam.output.Sample
import io.github.skorczanfff.capcam.output.SendRate
import io.github.skorczanfff.capcam.tracking.HeadAngles
import io.github.skorczanfff.capcam.tracking.HeadTracker
import io.github.skorczanfff.capcam.tracking.Mounting
import io.github.skorczanfff.capcam.tracking.OrientationSource
import io.github.skorczanfff.capcam.tracking.Quat
import io.github.skorczanfff.capcam.transport.Transport
import io.github.skorczanfff.capcam.transport.UdpTransport
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class StreamConfig(
    val host: String,
    val port: Int,
    val format: OutputFormat,
    val mounting: Mounting,
    val rate: SendRate,
)

data class StreamStats(
    val hz: Double = 0.0,
    val sent: Long = 0,
    val sendErrors: Long = 0,
    val angles: HeadAngles? = null,
    /** Set when streaming can't work (no sensor, bad address) or the last send failed. */
    val problem: String? = null,
)

/**
 * Sensor → head tracker → output format → UDP, all on one background thread.
 * [onStats] is called on the main thread about 4 times per second.
 */
class Streamer(
    context: Context,
    private val config: StreamConfig,
    private val onStats: (StreamStats) -> Unit,
    /** Called on the main thread whenever "forward" is captured (after Start and after each recenter). */
    private val onZeroed: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val source = OrientationSource(appContext)
    private val tracker = HeadTracker(config.mounting)
    private val limiter = RateLimiter(config.rate.hz)
    private val thread = HandlerThread("capcam-stream", Process.THREAD_PRIORITY_URGENT_DISPLAY)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var handler: Handler

    private val buffer = ByteBuffer.allocate(config.format.packetSize).order(ByteOrder.LITTLE_ENDIAN)
    private var transport: Transport? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Touched only on the stream thread.
    private var sequence = 0
    private var sent = 0L
    private var sendErrors = 0L
    private var lastSendError: String? = null
    private var windowStartNs = 0L
    private var windowSent = 0
    private var paused = false

    fun start() {
        acquireWifiLock()
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            transport = try {
                UdpTransport(config.host, config.port)
            } catch (e: Exception) {
                publish(StreamStats(problem = "Bad target ${config.host}:${config.port}: ${e.message}"))
                return@post
            }
            windowStartNs = SystemClock.elapsedRealtimeNanos()
            if (!source.start(handler, config.rate.hz, ::onSample)) {
                publish(StreamStats(problem = "This phone has no game rotation vector sensor"))
            }
        }
    }

    fun recenter() {
        handler.post { tracker.recenter() }
    }

    /**
     * While paused, the neutral pose (looking straight ahead) is sent instead of the head pose,
     * so the game shows its normal camera without losing the connection.
     */
    fun setPaused(value: Boolean) {
        handler.post { paused = value }
    }

    fun stop() {
        handler.post {
            source.stop()
            transport?.close()
            transport = null
        }
        thread.quitSafely()
        wifiLock?.release()
        wifiLock = null
    }

    private fun onSample(deviceToWorld: Quat, timestampNs: Long) {
        val wasZeroed = tracker.zeroYaw != null
        val tracked = tracker.process(deviceToWorld, timestampNs)
        if (!wasZeroed && tracker.zeroYaw != null) {
            Log.i(TAG, "Zeroed yaw at sample $sequence (raw head yaw ${"%.1f".format(tracker.zeroYaw)}°, device $deviceToWorld)")
            main.post(onZeroed)
        } else if (sequence < 3) {
            Log.i(TAG, "Sample $sequence: device $deviceToWorld")
        }
        // The tracker sees every sample (settling, zeroing); only the packets are rate-limited.
        if (!limiter.shouldSend(timestampNs)) return
        val head = if (paused) Quat.IDENTITY else tracked
        val sample = Sample(head, head.toHeadAngles(), sequence++, timestampNs)

        buffer.clear()
        config.format.encode(sample, buffer)
        try {
            transport?.send(buffer.array(), buffer.position())
            sent++
            lastSendError = null
        } catch (e: IOException) {
            sendErrors++
            lastSendError = "Send failed: ${e.message}"
        }

        windowSent++
        val now = SystemClock.elapsedRealtimeNanos()
        val elapsedNs = now - windowStartNs
        if (elapsedNs >= STATS_INTERVAL_NS) {
            publish(
                StreamStats(
                    hz = windowSent * 1e9 / elapsedNs,
                    sent = sent,
                    sendErrors = sendErrors,
                    angles = sample.angles,
                    problem = lastSendError,
                )
            )
            windowStartNs = now
            windowSent = 0
        }
    }

    private fun publish(stats: StreamStats) {
        main.post { onStats(stats) }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        val wifi = appContext.getSystemService(WifiManager::class.java) ?: return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifi.createWifiLock(mode, "capcam:stream").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private companion object {
        const val TAG = "CapCam"
        const val STATS_INTERVAL_NS = 250_000_000L
    }
}

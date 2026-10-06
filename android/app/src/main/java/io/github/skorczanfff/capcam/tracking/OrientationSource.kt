package io.github.skorczanfff.capcam.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/**
 * Reads TYPE_GAME_ROTATION_VECTOR (gyroscope + accelerometer, no magnetometer) at the
 * fastest rate the device allows and delivers device → world quaternions on [handler]'s thread.
 */
class OrientationSource(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    val hasGyroscope: Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    private var listener: SensorEventListener? = null

    /** Returns false when the device has no game rotation vector sensor. */
    fun start(handler: Handler, onSample: (deviceToWorld: Quat, timestampNs: Long) -> Unit): Boolean {
        val sensor = sensor ?: return false
        val wxyz = FloatArray(4)
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getQuaternionFromVector(wxyz, event.values)
                val q = Quat(wxyz[1].toDouble(), wxyz[2].toDouble(), wxyz[3].toDouble(), wxyz[0].toDouble())
                onSample(q, event.timestamp)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listener = l
        return sensorManager.registerListener(l, sensor, SensorManager.SENSOR_DELAY_FASTEST, handler)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }
}

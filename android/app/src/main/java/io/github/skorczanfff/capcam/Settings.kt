package io.github.skorczanfff.capcam

import android.content.Context
import io.github.skorczanfff.capcam.output.OutputFormat
import io.github.skorczanfff.capcam.tracking.Mounting

/** Last used target and presets, kept in SharedPreferences. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("capcam", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) = prefs.edit().putString("host", value).apply()

    var format: OutputFormat
        get() = OutputFormat.ALL.firstOrNull { it.label == prefs.getString("format", null) }
            ?: OutputFormat.ALL.first()
        set(value) = prefs.edit().putString("format", value.label).apply()

    /** Port is remembered per format, so switching formats restores the right one. */
    fun port(format: OutputFormat): Int = prefs.getInt("port.${format.label}", format.defaultPort)

    fun setPort(format: OutputFormat, port: Int) = prefs.edit().putInt("port.${format.label}", port).apply()

    var dimWhileStreaming: Boolean
        get() = prefs.getBoolean("dim", true)
        set(value) = prefs.edit().putBoolean("dim", value).apply()

    var mounting: Mounting
        get() = Mounting.ALL.firstOrNull {
            it.facing.name == prefs.getString("mount.facing", null) &&
                it.topEdge.name == prefs.getString("mount.topEdge", null)
        } ?: Mounting.DEFAULT
        set(value) = prefs.edit()
            .putString("mount.facing", value.facing.name)
            .putString("mount.topEdge", value.topEdge.name)
            .apply()
}

package io.github.skorczanfff.capcam

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.skorczanfff.capcam.output.OutputFormat
import io.github.skorczanfff.capcam.tracking.Facing
import io.github.skorczanfff.capcam.tracking.HeadDirection
import io.github.skorczanfff.capcam.tracking.Mounting
import io.github.skorczanfff.capcam.tracking.OrientationSource
import io.github.skorczanfff.capcam.ui.Brand
import io.github.skorczanfff.capcam.ui.BrandTheme
import io.github.skorczanfff.capcam.ui.GradientButton
import io.github.skorczanfff.capcam.ui.OutlineButton
import io.github.skorczanfff.capcam.ui.Pill
import io.github.skorczanfff.capcam.ui.brandChipColors
import io.github.skorczanfff.capcam.ui.brandRadioColors
import io.github.skorczanfff.capcam.ui.brandSwitchColors
import io.github.skorczanfff.capcam.ui.brandTextFieldColors
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var settings: Settings
    private var streamer: Streamer? = null

    private var running by mutableStateOf(false)
    private var stats by mutableStateOf(StreamStats())
    private var lastStatsAt by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val sensorInfo = describeSensors(OrientationSource(this))

        setContent {
            BrandTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CapCamScreen(sensorInfo)
                }
            }
        }
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    private fun startStreaming(config: StreamConfig, dim: Boolean) {
        stats = StreamStats()
        lastStatsAt = SystemClock.elapsedRealtime()
        streamer = Streamer(this, config) { s ->
            stats = s
            lastStatsAt = SystemClock.elapsedRealtime()
        }.also { it.start() }
        running = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (dim) window.attributes = window.attributes.apply { screenBrightness = 0.01f }
    }

    private fun stopStreaming() {
        streamer?.stop()
        streamer = null
        running = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    @Composable
    private fun CapCamScreen(sensorInfo: String) {
        var host by remember { mutableStateOf(settings.host) }
        var format by remember { mutableStateOf(settings.format) }
        var portText by remember { mutableStateOf(settings.port(settings.format).toString()) }
        var mounting by remember { mutableStateOf(settings.mounting) }
        var dim by remember { mutableStateOf(settings.dimWhileStreaming) }

        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 }
        val canStart = host.isNotBlank() && port != null

        // Notice when the sensor goes quiet: stats stop arriving.
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(running) {
            while (running) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
        val stale = running && now - lastStatsAt > 1500

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CapCam",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand.RealWhite,
                    modifier = Modifier.weight(1f),
                )
                when {
                    !running -> Pill("stopped", null)
                    stale || stats.problem != null -> Pill("problem", Brand.OrangeGradient)
                    else -> Pill("streaming", Brand.RaspberryGradient)
                }
            }

            LivePanel(stale, "${format.label} → ${host.ifBlank { "?" }}:$portText")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val buttonModifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                if (running) {
                    OutlineButton("Stop", enabled = true, onClick = ::stopStreaming, modifier = buttonModifier)
                } else {
                    GradientButton(
                        "Start",
                        enabled = canStart,
                        onClick = {
                            settings.host = host
                            settings.format = format
                            settings.setPort(format, port!!)
                            settings.mounting = mounting
                            settings.dimWhileStreaming = dim
                            startStreaming(StreamConfig(host, port, format, mounting), dim)
                        },
                        modifier = buttonModifier,
                    )
                }
                if (running) {
                    GradientButton("Recenter", enabled = true, onClick = { streamer?.recenter() }, modifier = buttonModifier)
                } else {
                    OutlineButton("Recenter", enabled = false, onClick = {}, modifier = buttonModifier)
                }
            }

            HorizontalDivider(color = Brand.DeepBlue)
            val editable = !running
            if (running) Text("Stop streaming to change settings", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)

            SectionTitle("Output")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.ALL.forEach { f ->
                    FilterChip(
                        selected = f == format,
                        enabled = editable,
                        onClick = {
                            port?.let { settings.setPort(format, it) }
                            format = f
                            portText = settings.port(f).toString()
                        },
                        label = { Text("${f.label} (${f.defaultPort})") },
                        colors = brandChipColors(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    enabled = editable,
                    label = { Text("PC address") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = brandTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    enabled = editable,
                    label = { Text("Port") },
                    isError = port == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = brandTextFieldColors(),
                    modifier = Modifier.width(104.dp),
                )
            }

            SectionTitle("Phone position")
            Column {
                Facing.entries.forEach { f ->
                    val pick = {
                        // Keep the top edge when it still fits the new facing (left/right fit all of them).
                        val top = mounting.topEdge.takeIf { it in f.topEdges } ?: f.topEdges.first()
                        mounting = Mounting(f, top)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = f == mounting.facing, enabled = editable, onClick = pick),
                    ) {
                        RadioButton(
                            selected = f == mounting.facing,
                            enabled = editable,
                            onClick = pick,
                            colors = brandRadioColors(),
                        )
                        Text(f.label, color = if (editable) Brand.White else Brand.Muted)
                    }
                }
            }

            SectionTitle("Top edge of the phone points")
            EdgeRow("Landscape", mounting.facing.landscapeEdges, mounting, editable) { mounting = it }
            EdgeRow("Portrait", mounting.facing.portraitEdges, mounting, editable) { mounting = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dim screen while streaming", modifier = Modifier.weight(1f))
                Switch(checked = dim, enabled = editable, onCheckedChange = { dim = it }, colors = brandSwitchColors())
            }

            Text(sensorInfo, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Brand.RealWhite, fontWeight = FontWeight.SemiBold)
    }

    @Composable
    private fun EdgeRow(
        title: String,
        edges: List<HeadDirection>,
        mounting: Mounting,
        editable: Boolean,
        onPick: (Mounting) -> Unit,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Brand.Muted, modifier = Modifier.width(88.dp))
            edges.forEach { d ->
                FilterChip(
                    selected = d == mounting.topEdge,
                    enabled = editable,
                    onClick = { onPick(Mounting(mounting.facing, d)) },
                    label = { Text(d.label) },
                    colors = brandChipColors(),
                )
            }
        }
    }

    @Composable
    private fun LivePanel(stale: Boolean, target: String) {
        val s = stats
        val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        val hz = if (running && !stale) s.hz else 0.0
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brand.DeepBlue)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "%.0f", hz),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = if (running) Brand.RealWhite else Brand.Muted,
                )
                Text(" Hz", style = mono, color = Brand.Muted, modifier = Modifier.weight(1f))
                if (running && hz < 100) Pill("below 100 Hz", Brand.OrangeGradient)
            }
            AngleRow("yaw", s.angles?.yaw, mono)
            AngleRow("pitch", s.angles?.pitch, mono)
            AngleRow("roll", s.angles?.roll, mono)
            Text("sent ${s.sent} · errors ${s.sendErrors}", style = mono, color = Brand.Muted)
            Text(target, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
            if (stale) Alert("No sensor data")
            s.problem?.let { Alert(it) }
        }
    }

    /** White text on the orange gradient: orange text alone is too dark on the blue panel. */
    @Composable
    private fun Alert(text: String) {
        Text(
            text,
            color = Brand.RealWhite,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Brand.OrangeGradient)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    /** Label, value and a bar that grows from the centre: full width = ±90°. */
    @Composable
    private fun AngleRow(label: String, degrees: Double?, style: TextStyle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.padEnd(6), style = style, color = Brand.Muted)
            Text(
                degrees?.let { String.format(Locale.US, "%7.1f°", it) } ?: "      –",
                style = style,
                color = Brand.RealWhite,
                modifier = Modifier.width(96.dp),
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .padding(start = 8.dp),
            ) {
                val mid = size.width / 2
                drawRect(Brand.Track, Offset(0f, size.height / 2 - 1), Size(size.width, 2f))
                if (degrees != null) {
                    val fraction = (degrees / 90.0).coerceIn(-1.0, 1.0).toFloat()
                    val w = abs(fraction) * mid
                    val left = if (fraction >= 0) mid else mid - w
                    drawRoundRect(
                        Brand.RaspberryGradient,
                        Offset(left, 1f),
                        Size(w, size.height - 2),
                        CornerRadius(3f, 3f),
                    )
                }
                drawRect(Brand.White, Offset(mid - 1, 0f), Size(2f, size.height))
            }
        }
    }

    private fun describeSensors(source: OrientationSource): String {
        val sensor = source.sensor
        val gyro = if (source.hasGyroscope) "gyroscope: yes" else "gyroscope: NO (tracking will be poor)"
        val rv = sensor?.let {
            val maxHz = if (it.minDelay > 0) " · up to ${1_000_000 / it.minDelay} Hz" else ""
            "game rotation vector: ${it.name} (${it.vendor})$maxHz"
        } ?: "game rotation vector: NOT AVAILABLE"
        return "$gyro\n$rv"
    }
}

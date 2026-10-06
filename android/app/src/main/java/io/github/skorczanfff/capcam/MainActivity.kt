package io.github.skorczanfff.capcam

import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
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
import io.github.skorczanfff.capcam.tracking.MountPreset
import io.github.skorczanfff.capcam.tracking.Mounting
import io.github.skorczanfff.capcam.tracking.OrientationSource
import io.github.skorczanfff.capcam.ui.Brand
import io.github.skorczanfff.capcam.ui.BrandTheme
import io.github.skorczanfff.capcam.ui.GradientButton
import io.github.skorczanfff.capcam.ui.MountTile
import io.github.skorczanfff.capcam.ui.describe
import io.github.skorczanfff.capcam.ui.OutlineButton
import io.github.skorczanfff.capcam.ui.Pill
import io.github.skorczanfff.capcam.ui.brandChipColors
import io.github.skorczanfff.capcam.ui.brandRadioColors
import io.github.skorczanfff.capcam.ui.brandSwitchColors
import io.github.skorczanfff.capcam.ui.brandTextFieldColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.skorczanfff.capcam.net.Ipv4
import io.github.skorczanfff.capcam.net.LocalNetwork
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var settings: Settings
    private var streamer: Streamer? = null

    private var running by mutableStateOf(false)
    private var paused by mutableStateOf(false)
    private var stats by mutableStateOf(StreamStats())
    private var lastStatsAt by mutableLongStateOf(0L)
    private var screenMode by mutableStateOf(ScreenMode.DIM)

    /** In Black mode a tap shows the app for a few seconds. */
    private var revealed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        screenMode = settings.screenMode
        val sensorInfo = describeSensors(OrientationSource(this))

        setContent {
            BrandTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CapCamScreen(sensorInfo)
                    }
                    BlackOverlay()
                }
            }
        }
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    private fun startStreaming(config: StreamConfig) {
        stats = StreamStats()
        lastStatsAt = SystemClock.elapsedRealtime()
        streamer = Streamer(
            this,
            config,
            onStats = { s ->
                stats = s
                lastStatsAt = SystemClock.elapsedRealtime()
            },
            onZeroed = ::buzz,
        ).also { it.start() }
        running = true
        paused = false
        revealed = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness()
    }

    private fun stopStreaming() {
        streamer?.stop()
        streamer = null
        running = false
        paused = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness()
    }

    private fun setScreen(mode: ScreenMode) {
        screenMode = mode
        settings.screenMode = mode
        revealed = false
        applyBrightness()
    }

    /** Minimum brightness while streaming in Dim and Black mode, the system's brightness otherwise. */
    private fun applyBrightness() {
        val low = running && screenMode != ScreenMode.ON
        window.attributes = window.attributes.apply {
            screenBrightness = if (low) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    /** Covers everything, system bars included, with black while streaming in Black mode. */
    @Composable
    private fun BlackOverlay() {
        val show = running && screenMode == ScreenMode.BLACK && !revealed
        LaunchedEffect(show) {
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            if (show) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        LaunchedEffect(revealed) {
            if (revealed) {
                delay(10_000)
                revealed = false
            }
        }
        if (!show) return
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    revealed = true
                },
        ) {
            // Barely visible, so the screen stays effectively off but you can tell the app is alive.
            Text("CapCam · tap to show", color = Color(0xFF161616), style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun CapCamScreen(sensorInfo: String) {
        var host by remember { mutableStateOf(settings.host) }
        var format by remember { mutableStateOf(settings.format) }
        var portText by remember { mutableStateOf(settings.port(settings.format).toString()) }
        var mounting by remember { mutableStateOf(settings.mounting) }

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
                    paused -> Pill("default view", null)
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
                            startStreaming(StreamConfig(host, port, format, mounting))
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
            Text(
                "Recenter: look straight at the monitor and press a volume button on the phone " +
                    "(or Recenter here). The phone buzzes when it's done.",
                color = Brand.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            // Sends the neutral pose so the game shows its normal camera; tracking keeps running.
            val pauseModifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
            if (paused) {
                GradientButton("Resume head tracking", enabled = running, onClick = { pauseTracking(false) }, modifier = pauseModifier)
            } else {
                OutlineButton("Default game camera", enabled = running, onClick = { pauseTracking(true) }, modifier = pauseModifier)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Screen while streaming", modifier = Modifier.weight(1f))
                ScreenMode.entries.forEach { m ->
                    FilterChip(
                        selected = m == screenMode,
                        onClick = { setScreen(m) },
                        label = { Text(m.label) },
                        colors = brandChipColors(),
                    )
                }
            }
            if (screenMode == ScreenMode.BLACK) {
                Text(
                    "Black: the screen goes fully dark while streaming (on this kind of screen the pixels are off), " +
                        "but tracking and the volume buttons keep working. Tap it to see the app for 10 s.",
                    color = Brand.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
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
            NetworkHints(host)

            SectionTitle("How you wear the phone")
            val preset = MountPreset.of(mounting)
            val isFlipped = preset != null && mounting != preset.mounting
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MountPreset.entries.forEach { p ->
                    // Show each tile as it would be picked: switching tiles keeps the 180° choice.
                    val option = if (isFlipped) p.mounting.flipped() else p.mounting
                    MountTile(
                        preset = p,
                        mounting = if (p == preset) mounting else option,
                        selected = p == preset,
                        enabled = editable,
                        onClick = { mounting = option },
                    )
                }
            }
            if (preset == null) {
                Text("Custom: ${describe(mounting)}", color = Brand.RealWhite, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Phone turned 180°")
                    Text(
                        "Swaps the top edge (the end with the front camera): left ↔ right, up ↔ down.",
                        color = Brand.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = isFlipped,
                    enabled = editable && preset != null,
                    onCheckedChange = { mounting = mounting.flipped() },
                    colors = brandSwitchColors(),
                )
            }

            var showCustom by remember { mutableStateOf(preset == null) }
            OutlineButton(
                if (showCustom) "Hide custom position" else "Custom position…",
                enabled = true,
                onClick = { showCustom = !showCustom },
                modifier = Modifier.fillMaxWidth(),
            )
            if (showCustom) {
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
                Text("Top edge of the phone points", color = Brand.Muted)
                EdgeRow("Landscape", mounting.facing.landscapeEdges, mounting, editable) { mounting = it }
                EdgeRow("Portrait", mounting.facing.portraitEdges, mounting, editable) { mounting = it }
            }

            Text(sensorInfo, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    /** Volume buttons recenter while streaming: they're reachable with the phone on your head. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (running && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.repeatCount == 0) streamer?.recenter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (running && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    /** Short buzz when "forward" is captured, so you know it worked without looking at the screen. */
    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        // Two short taps. Marked as feedback for a physical button: the default (touch) usage is
        // silenced when touch haptics are off, which made the buzz never arrive on some phones.
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 70, 90, 70), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
            )
        }
    }

    /** The phone's own address, and warnings when the PC address can't be on the same network. */
    @Composable
    private fun NetworkHints(host: String) {
        var local by remember { mutableStateOf(LocalNetwork.read(this)) }
        LaunchedEffect(Unit) {
            while (true) {
                local = LocalNetwork.read(this@MainActivity)
                delay(3_000)
            }
        }
        val phone = local.address
        if (phone == null) {
            Alert("This phone isn't on Wi-Fi. Connect it to the same network as your PC.")
        } else {
            val hint = Ipv4.commonPrefix(phone, local.prefixLength)
                ?.let { " · your PC's address probably starts with $it" } ?: ""
            Text("This phone: $phone$hint", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
            if (Ipv4.sameSubnet(host, phone, local.prefixLength) == false) {
                Alert(
                    "$host isn't on this phone's network. Check the PC's address " +
                        "(README › Find your PC's address) and turn off any VPN on the PC."
                )
            }
        }
        if (local.vpnActive) {
            Alert("A VPN is on on this phone. Turn it off: it usually blocks the connection to your PC.")
        }
    }

    private fun pauseTracking(value: Boolean) {
        paused = value
        streamer?.setPaused(value)
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

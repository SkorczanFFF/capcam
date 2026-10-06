package io.github.skorczanfff.capcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.skorczanfff.capcam.tracking.Facing
import io.github.skorczanfff.capcam.tracking.HeadDirection
import io.github.skorczanfff.capcam.tracking.MountPreset
import io.github.skorczanfff.capcam.tracking.Mounting

/**
 * Full-width selectable tile: a side view (you, your cap, the phone, the monitor in front of you)
 * and a plain-words description of the position.
 */
@Composable
fun MountTile(
    preset: MountPreset,
    mounting: Mounting,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val ink = if (enabled || selected) Brand.RealWhite else Brand.White.copy(alpha = 0.55f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (selected) Modifier.background(Brand.RaspberryGradient) else Modifier.background(Brand.DeepBlue))
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.RealWhite else Brand.White.copy(alpha = 0.35f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        Canvas(
            modifier = Modifier
                .width(132.dp)
                .height(88.dp),
        ) {
            drawSideView(ink, standing = mounting.facing != Facing.SCREEN_UP, landscape = mounting.isLandscape)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(preset.title, color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(preset.where, color = ink, style = MaterialTheme.typography.bodyMedium)
            Text(describe(mounting), color = ink, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "Screen up · portrait · top edge to your left" in plain words. */
fun describe(m: Mounting): String {
    val screen = when (m.facing) {
        Facing.SCREEN_UP -> "screen up"
        Facing.SCREEN_FORWARD -> "screen facing the monitor"
        Facing.SCREEN_BACK -> "screen facing you"
    }
    val top = when (m.topEdge) {
        HeadDirection.LEFT -> "top edge to your left"
        HeadDirection.RIGHT -> "top edge to your right"
        HeadDirection.UP -> "top edge up"
        HeadDirection.DOWN -> "top edge down"
        HeadDirection.FORWARD -> "top edge towards the monitor"
        HeadDirection.BACK -> "top edge towards you"
    }
    return "${screen.replaceFirstChar { it.uppercase() }} · ${if (m.isLandscape) "landscape" else "portrait"} · $top"
}

private fun DrawScope.line(ink: Color, from: Offset, to: Offset) =
    drawLine(ink, from, to, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

private fun DrawScope.arrow(ink: Color, from: Offset, to: Offset) {
    line(ink, from, to)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    val ux = dx / len
    val uy = dy / len
    val head = 5.dp.toPx()
    line(ink, to, Offset(to.x - ux * head - uy * head * 0.7f, to.y - uy * head + ux * head * 0.7f))
    line(ink, to, Offset(to.x - ux * head + uy * head * 0.7f, to.y - uy * head - ux * head * 0.7f))
}

/** You seen from the side, looking right at a monitor, with the phone on the cap brim. */
private fun DrawScope.drawSideView(ink: Color, standing: Boolean, landscape: Boolean) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = 2.dp.toPx())

    // Monitor in front of you: a wide screen on a stand, so it doesn't look like a phone.
    val monSize = Size(w * 0.24f, h * 0.30f)
    val mon = Offset(w - monSize.width - 1.dp.toPx(), h * 0.26f)
    drawRoundRect(ink, mon, monSize, CornerRadius(2.dp.toPx()), style = stroke)
    val standX = mon.x + monSize.width / 2
    line(ink, Offset(standX, mon.y + monSize.height), Offset(standX, h * 0.74f))
    line(ink, Offset(standX - monSize.width * 0.3f, h * 0.74f), Offset(standX + monSize.width * 0.3f, h * 0.74f))

    // Head with a nose pointing at the monitor.
    val r = h * 0.25f
    val c = Offset(w * 0.24f, h * 0.66f)
    drawCircle(ink, radius = r, center = c, style = stroke)
    val nose = Path().apply {
        moveTo(c.x + r * 0.95f, c.y - r * 0.05f)
        lineTo(c.x + r * 1.25f, c.y + r * 0.2f)
        lineTo(c.x + r * 0.9f, c.y + r * 0.35f)
    }
    drawPath(nose, ink, style = stroke)

    // Cap: crown over the head and a brim reaching towards the monitor.
    drawArc(
        ink,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(c.x - r * 1.05f, c.y - r * 1.15f),
        size = Size(r * 2.1f, r * 1.6f),
    )
    val brimY = c.y - r * 0.38f
    val brimStart = c.x + r * 0.6f
    val brimEnd = c.x + r * 1.9f
    line(ink, Offset(brimStart, brimY), Offset(brimEnd, brimY))

    val phoneX = (brimStart + brimEnd) / 2 + r * 0.2f
    if (!standing) {
        // Lying flat on the brim, screen up.
        val len = r * 1.1f
        drawRoundRect(
            ink,
            Offset(phoneX - len / 2, brimY - 5.dp.toPx()),
            Size(len, 4.dp.toPx()),
            CornerRadius(1.5f.dp.toPx()),
        )
        arrow(ink, Offset(phoneX, brimY - 8.dp.toPx()), Offset(phoneX, brimY - 22.dp.toPx()))
    } else {
        // Standing on the brim, screen towards the monitor.
        val tall = if (landscape) r * 0.75f else r * 1.35f
        val thick = 4.dp.toPx()
        drawRoundRect(
            ink,
            Offset(phoneX - thick / 2, brimY - tall),
            Size(thick, tall),
            CornerRadius(1.5f.dp.toPx()),
        )
        val y = brimY - tall / 2
        arrow(ink, Offset(phoneX + thick, y), Offset(mon.x - 4.dp.toPx(), y))
    }
}

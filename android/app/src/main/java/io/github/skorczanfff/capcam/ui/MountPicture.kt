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
import kotlin.math.sqrt

/**
 * Full-width selectable tile: a small side view of the cap with the phone on it, and a plain-words
 * description of the position.
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
                .width(96.dp)
                .height(72.dp),
        ) {
            drawCapIcon(ink, standing = mounting.facing != Facing.SCREEN_UP, landscape = mounting.isLandscape)
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

private fun DrawScope.arrow(ink: Color, from: Offset, to: Offset) {
    val stroke = 2.5.dp.toPx()
    drawLine(ink, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    val ux = dx / len
    val uy = dy / len
    val head = 6.dp.toPx()
    val spread = 0.8f
    drawLine(ink, to, Offset(to.x - ux * head - uy * head * spread, to.y - uy * head + ux * head * spread), stroke, StrokeCap.Round)
    drawLine(ink, to, Offset(to.x - ux * head + uy * head * spread, to.y - uy * head - ux * head * spread), stroke, StrokeCap.Round)
}

/**
 * Minimal side view: a cap with its brim pointing forward (right), the phone on the brim and an
 * arrow showing where the screen faces. The cap is muted so the phone and the arrow stand out.
 */
private fun DrawScope.drawCapIcon(ink: Color, standing: Boolean, landscape: Boolean) {
    val w = size.width
    val h = size.height
    val cap = ink.copy(alpha = 0.45f)
    val brimY = h * 0.72f
    val gap = 3.dp.toPx()
    val thick = 4.dp.toPx()
    val corner = CornerRadius(1.5.dp.toPx())

    // Crown: a soft dome from the back of the cap to the front.
    val crown = Path().apply {
        moveTo(w * 0.04f, brimY)
        cubicTo(w * 0.04f, h * 0.32f, w * 0.20f, h * 0.24f, w * 0.31f, h * 0.24f)
        cubicTo(w * 0.44f, h * 0.24f, w * 0.56f, h * 0.36f, w * 0.58f, brimY)
        close()
    }
    drawPath(crown, cap)
    // Brim: a thick rounded bar reaching forward, dipping slightly at the tip.
    drawLine(cap, Offset(w * 0.50f, brimY), Offset(w * 0.86f, brimY + h * 0.03f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)

    if (!standing) {
        // Lying flat on the brim, screen up.
        val len = w * 0.26f
        val cx = w * 0.70f
        drawRoundRect(ink, Offset(cx - len / 2, brimY - gap - thick), Size(len, thick), corner)
        arrow(ink, Offset(cx, brimY - gap - thick - 4.dp.toPx()), Offset(cx, brimY - gap - thick - 22.dp.toPx()))
    } else {
        // Standing on the brim, screen facing forward.
        val tall = if (landscape) h * 0.24f else h * 0.44f
        val x = w * 0.66f
        drawRoundRect(ink, Offset(x - thick / 2, brimY - gap - tall), Size(thick, tall), corner)
        val y = brimY - gap - tall / 2
        arrow(ink, Offset(x + thick, y), Offset(w - 3.dp.toPx(), y))
    }
}

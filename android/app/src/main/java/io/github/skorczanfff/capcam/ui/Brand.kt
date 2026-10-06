package io.github.skorczanfff.capcam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Brand palette. */
object Brand {
    val PrimaryBlue = Color(0xFF001A25)
    val DeepBlue = Color(0xFF0C2835)
    val RealWhite = Color(0xFFFFFFFF)
    val White = Color(0xFFE4E4E4)
    val Raspberry = Color(0xFF801834)
    val RaspberryDark = Color(0xFF820025)
    val Orange = Color(0xFF992210)
    val OrangeDark = Color(0xFF972B1A)

    val Muted = White.copy(alpha = 0.6f)
    val Track = White.copy(alpha = 0.15f)

    /** The brand gradient, raspberry → raspberry-dark. */
    val RaspberryGradient = Brush.horizontalGradient(listOf(Raspberry, RaspberryDark))
    val OrangeGradient = Brush.horizontalGradient(listOf(Orange, OrangeDark))
}

private val BrandColors = darkColorScheme(
    primary = Brand.Raspberry,
    onPrimary = Brand.RealWhite,
    primaryContainer = Brand.Raspberry,
    onPrimaryContainer = Brand.RealWhite,
    secondary = Brand.Raspberry,
    onSecondary = Brand.RealWhite,
    secondaryContainer = Brand.Raspberry,
    onSecondaryContainer = Brand.RealWhite,
    background = Brand.PrimaryBlue,
    onBackground = Brand.White,
    surface = Brand.PrimaryBlue,
    onSurface = Brand.White,
    surfaceVariant = Brand.DeepBlue,
    onSurfaceVariant = Brand.White,
    outline = Brand.White.copy(alpha = 0.45f),
    outlineVariant = Brand.DeepBlue,
    error = Brand.Orange,
    onError = Brand.RealWhite,
)

@Composable
fun BrandTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = BrandColors, content = content)

private val ButtonShape = RoundedCornerShape(50)

/** Primary action: white text on the raspberry gradient. */
@Composable
fun GradientButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Brand.RealWhite,
            disabledContainerColor = Brand.DeepBlue,
            disabledContentColor = Brand.Muted,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .clip(ButtonShape)
            .then(if (enabled) Modifier.background(Brand.RaspberryGradient) else Modifier),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary action: white outline. */
@Composable
fun OutlineButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Brand.White else Brand.Track),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Brand.RealWhite,
            disabledContentColor = Brand.Muted,
        ),
        modifier = modifier,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Small status badge with white text on a brand fill (keeps text contrast high on the dark blue). */
@Composable
fun Pill(text: String, fill: Brush?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = if (fill != null) Brand.RealWhite else Brand.Muted,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(shape)
            .then(if (fill != null) Modifier.background(fill) else Modifier.background(Brand.DeepBlue))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// Raspberry is too dark for thin lines on primary blue, so focus/selection indicators use white.

@Composable
fun brandTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand.RealWhite,
    focusedLabelColor = Brand.RealWhite,
    cursorColor = Brand.RealWhite,
    unfocusedBorderColor = Brand.White.copy(alpha = 0.45f),
    unfocusedLabelColor = Brand.Muted,
    errorBorderColor = Brand.Orange,
    errorLabelColor = Brand.White,
)

@Composable
fun brandRadioColors() = RadioButtonDefaults.colors(
    selectedColor = Brand.RealWhite,
    unselectedColor = Brand.Muted,
)

@Composable
fun brandSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Brand.RealWhite,
    checkedTrackColor = Brand.Raspberry,
    checkedBorderColor = Brand.RealWhite,
    uncheckedThumbColor = Brand.Muted,
    uncheckedTrackColor = Brand.DeepBlue,
    uncheckedBorderColor = Brand.Muted,
)

@Composable
fun brandChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Brand.Raspberry,
    selectedLabelColor = Brand.RealWhite,
    labelColor = Brand.White,
)

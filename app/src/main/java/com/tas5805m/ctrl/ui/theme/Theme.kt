package com.bgap.speaker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Token warna dari preview HTML
object BgapColors {
    val Primary = Color(0xFF7DD3FC)      // cyan
    val Accent = Color(0xFFFBBF24)       // amber
    val Success = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)

    val MeterGreen = Color(0xFF22C55E)
    val MeterLime = Color(0xFF84CC16)
    val MeterYellow = Color(0xFFFBBF24)
    val MeterOrange = Color(0xFFF97316)
    val MeterRed = Color(0xFFEF4444)
    val MeterSubStart = Color(0xFFCA8A04)

    val TextPrimary = Color(0xFFE5E7EB)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)
    val TextDim = Color(0xFF64748B)

    val PanelDark = Color(0xFF05070A)
    val SurfaceVariant = Color(0xFF121212)
}

private val AmoledDarkColors = darkColorScheme(
    primary = BgapColors.Primary,
    onPrimary = Color(0xFF001824),
    secondary = BgapColors.Accent,
    onSecondary = Color(0xFF1F1300),
    background = Color.Black,
    onBackground = BgapColors.TextPrimary,
    surface = Color.Black,
    onSurface = BgapColors.TextPrimary,
    surfaceVariant = BgapColors.SurfaceVariant,
    onSurfaceVariant = BgapColors.TextMuted,
    outline = Color(0xFF334155),
    error = BgapColors.Danger,
    onError = Color.Black
)

@Composable
fun BgapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoled -> AmoledDarkColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

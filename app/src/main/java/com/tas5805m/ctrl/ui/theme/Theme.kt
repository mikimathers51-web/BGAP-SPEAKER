package com.tas5805m.ctrl.ui.theme

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
import com.tas5805m.ctrl.data.ThemeMode

// Palet "Glass": panel hampir hitam (OLED), kartu kaca tipis, cyan #7DD3FC + amber #FBBF24.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF0B3A52),
    onPrimaryContainer = Color(0xFFC6ECFF),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF00391A),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF05070A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF05070A),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1B1D20),
    onSurfaceVariant = Color(0xFF94A3B8),
    // "Kaca": putih 4–12% di atas panel #05070A
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0D0F12),
    surfaceContainer = Color(0xFF111316),
    surfaceContainerHigh = Color(0xFF17191C),
    surfaceContainerHighest = Color(0xFF141619),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF1E2022),
    error = Color(0xFFF87171),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6EEFF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF1B6B3A),
    tertiary = Color(0xFF8A5100),
    background = Color(0xFFF5F8FC),
    surface = Color(0xFFF5F8FC),
    surfaceVariant = Color(0xFFDDE4EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF3F9),
    surfaceContainer = Color(0xFFE9EEF5),
    surfaceContainerHigh = Color(0xFFE2E8F1),
    surfaceContainerHighest = Color(0xFFD8DFEA),
)

/** Warna status (tetap sama di semua tema agar maknanya konsisten). */
val OkGreen = Color(0xFF4ADE80)
val WarnAmber = Color(0xFFFBBF24)
val BadRed = Color(0xFFF87171)

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun TasTheme(
    mode: ThemeMode = ThemeMode.DARK,
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = resolveDark(mode)
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

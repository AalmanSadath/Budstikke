package dev.aalman.budstikke.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * BifroSSH's three palettes (src/styles/tokens.css), mapped onto Material 3 roles.
 *
 * The mapping is a first pass. BifroSSH names surfaces as a ramp away from the page
 * (bg-darkest, bg-dark, bg-mid, bg-hover); here the page is `background`/`surface` and
 * each step along the ramp is the next surface container. Text on accent and danger keeps
 * the per-theme contrast choice BifroSSH made (on-accent, on-danger).
 */

enum class Palette { Dynamic, BifrosshDark, BifrosshLight, BifrosshAmoled }

val BifrosshDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF21262D),
    onPrimaryContainer = Color(0xFF79C0FF),
    secondary = Color(0xFF79C0FF),
    onSecondary = Color(0xFF0D1117),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = Color(0xFF0D1117),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF21262D),
    surfaceContainerHighest = Color(0xFF2D333B),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF30363D),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF0D1117),
)

val BifrosshAmoled: ColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF111111),
    onPrimaryContainer = Color(0xFFC4B0FF),
    secondary = Color(0xFFC4B0FF),
    onSecondary = Color(0xFF0D1117),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF888888),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color(0xFF222222),
    outlineVariant = Color(0xFF222222),
    error = Color(0xFFFF1744),
    onError = Color(0xFF0D1117),
)

val BifrosshLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF155397),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E6EC),
    onPrimaryContainer = Color(0xFF0F4478),
    secondary = Color(0xFF0F4478),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1B2331),
    surface = Color(0xFFF4F6F9),
    onSurface = Color(0xFF1B2331),
    surfaceVariant = Color(0xFFE1E6EC),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEAEEF3),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE1E6EC),
    surfaceContainerHighest = Color(0xFFD9DEE5),
    outline = Color(0xFFBFC8D2),
    outlineVariant = Color(0xFFBFC8D2),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
)

/**
 * Dynamic colour where the system offers it (Android 12 and later), BifroSSH dark or light
 * otherwise, following the system's dark mode. A setting to pick the palette comes later.
 */
@Composable
fun BudstikkeTheme(
    palette: Palette = Palette.Dynamic,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when (palette) {
        Palette.Dynamic -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> BifrosshDark
            else -> BifrosshLight
        }
        Palette.BifrosshDark -> BifrosshDark
        Palette.BifrosshLight -> BifrosshLight
        Palette.BifrosshAmoled -> BifrosshAmoled
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

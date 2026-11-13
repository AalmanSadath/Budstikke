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
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.provider.Settings
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.aalman.budstikke.settings.SettingsRepository
import dev.aalman.budstikke.settings.ThemeMode
import dev.aalman.budstikke.settings.ThemeSource

/*
 * BifroSSH's three palettes (src/styles/tokens.css), mapped onto Material 3 roles.
 *
 * The mapping is a first pass. BifroSSH names surfaces as a ramp away from the page
 * (bg-darkest, bg-dark, bg-mid, bg-hover); here the page is `background`/`surface` and
 * each step along the ramp is the next surface container. Text on accent and danger keeps
 * the per-theme contrast choice BifroSSH made (on-accent, on-danger).
 */

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

/** Whether this device can take colours from the wallpaper (Android 12 and later). */
val dynamicColourAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The app's colours, from the user's settings: wallpaper colours or BifroSSH's palettes,
 * light or dark by the system or by hand, true black in dark mode if chosen, and a chosen
 * accent over any of them. Reads the settings itself, so a change applies at once.
 */
@Composable
fun BudstikkeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsRepository.get(context).settings.collectAsState()
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    var scheme = when {
        settings.themeSource == ThemeSource.Dynamic && dynamicColourAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        !dark -> BifrosshLight
        settings.trueBlack -> BifrosshAmoled
        else -> BifrosshDark
    }
    // BifroSSH's AMOLED palette is already black; dynamic colour needs its surfaces taken there.
    if (dark && settings.trueBlack && scheme !== BifrosshAmoled) scheme = scheme.trueBlack()
    settings.accent?.let { scheme = scheme.withAccent(it) }
    val a11y = settings.accessibility
    if (a11y.useHighContrast) scheme = scheme.highContrast(dark)
    val resolver = context.contentResolver
    // Animations off in the system's settings count as reduced motion too.
    val systemNoMotion = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val density = LocalDensity.current
    // The status and navigation bar icons follow the app's light or dark, which can be set by
    // hand apart from the system's; otherwise dark icons can sit on a dark app.
    val activity = LocalActivity.current
    val view = LocalView.current
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalHighContrast provides a11y.useHighContrast,
        LocalReducedMotion provides (a11y.useReducedMotion || systemNoMotion),
        // Beyond the system's own text size: every sp is this much larger again.
        LocalDensity provides if (a11y.useLargerText) Density(density.density, density.fontScale * LARGER_TEXT) else density,
        LocalMinimumInteractiveComponentSize provides if (a11y.useLargerTargets) LARGE_TARGET else DEFAULT_TARGET,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** How much accessibility mode's larger text scales text beyond the system's text size. */
const val LARGER_TEXT = 1.3f

/** Material's own minimum touch target, and accessibility mode's larger one. */
val DEFAULT_TARGET = 48.dp
val LARGE_TARGET = 56.dp

/** Whether accessibility mode's high contrast is on, for shapes that need an outline to stand out. */
val LocalHighContrast = staticCompositionLocalOf { false }

/** Whether animations should be left out: accessibility mode's reduced motion, or animations off in the system. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * The size of a tappable control drawn at [base]: [base] itself, or at least [LARGE_TARGET]
 * when accessibility mode asks for larger touch targets. Material's own controls follow
 * [LocalMinimumInteractiveComponentSize] by themselves; this is for the app's own.
 */
@Composable
fun touchTarget(base: Dp): Dp =
    if (LocalMinimumInteractiveComponentSize.current >= LARGE_TARGET) maxOf(base, LARGE_TARGET) else base

private fun ColorScheme.trueBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF1A1A1A),
)

/** Replaces the accent roles with a chosen colour and the text colour that reads on it. */
internal fun ColorScheme.withAccent(rgb: Int): ColorScheme {
    val on = Color(0xFF000000 or onAccent(rgb).toLong())
    // A mid accent may fall just short of 4.5:1 with either text colour; take it a little
    // further from its text until it reads.
    val accent = strengthen(Color(0xFF000000 or rgb.toLong()), if (on == Color.White) Color.Black else Color.White, listOf(on), NORMAL_CONTRAST)
    val shade = Color(0xFF000000 or accentShade(rgb).toLong())
    return copy(
        primary = accent,
        onPrimary = on,
        secondary = shade,
        onSecondary = Color(0xFF000000 or onAccent(accentShade(rgb)).toLong()),
        inversePrimary = accent,
        surfaceTint = accent,
    )
}

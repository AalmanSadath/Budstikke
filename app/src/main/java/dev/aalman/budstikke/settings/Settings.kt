package dev.aalman.budstikke.settings

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the colours come from: the wallpaper (Android 12 and later) or BifroSSH's palettes. */
enum class ThemeSource { Dynamic, Bifrossh }

/** Light or dark: following the system, or set by hand. */
enum class ThemeMode { System, Light, Dark }

data class Settings(
    val themeSource: ThemeSource = ThemeSource.Dynamic,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Pure black backgrounds in dark mode, for AMOLED screens. */
    val trueBlack: Boolean = false,
    /** A chosen accent as 0xRRGGBB, or null to use the theme's own. */
    val accent: Int? = null,
    /** The SIM new messages go out on, or INVALID_SUBSCRIPTION_ID for the system's default. */
    val defaultSubscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    /** Seconds a sent message waits, and can be cancelled, before it goes out; 0 for none. */
    val sendDelaySeconds: Int = 0,
    /** Accessibility mode, with each of its parts that can be turned off on its own. */
    val accessibility: Accessibility = Accessibility(),
)

/**
 * Accessibility mode: one switch, and its four parts, each on by default once the mode is
 * on. A part counts only while the mode is on, so turning the mode off undoes all of them
 * and turning it on again brings back the same choice.
 */
data class Accessibility(
    val on: Boolean = false,
    val highContrast: Boolean = true,
    val largerText: Boolean = true,
    val largerTargets: Boolean = true,
    val reducedMotion: Boolean = true,
) {
    val useHighContrast: Boolean get() = on && highContrast
    val useLargerText: Boolean get() = on && largerText
    val useLargerTargets: Boolean get() = on && largerTargets
    val useReducedMotion: Boolean get() = on && reducedMotion
}

/** The send delays Settings offers, in seconds. */
val SEND_DELAYS = listOf(0, 3, 5, 10, 30)

/**
 * The app's settings, kept in its private preferences on this device. Every screen reads
 * them from [settings], so a change applies at once.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<Settings> = state.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        prefs.edit {
            putString(KEY_SOURCE, next.themeSource.name)
            putString(KEY_MODE, next.themeMode.name)
            putBoolean(KEY_TRUE_BLACK, next.trueBlack)
            if (next.accent != null) putInt(KEY_ACCENT, next.accent) else remove(KEY_ACCENT)
            putInt(KEY_SUBSCRIPTION, next.defaultSubscriptionId)
            putInt(KEY_SEND_DELAY, next.sendDelaySeconds)
            putBoolean(KEY_A11Y, next.accessibility.on)
            putBoolean(KEY_A11Y_CONTRAST, next.accessibility.highContrast)
            putBoolean(KEY_A11Y_TEXT, next.accessibility.largerText)
            putBoolean(KEY_A11Y_TARGETS, next.accessibility.largerTargets)
            putBoolean(KEY_A11Y_MOTION, next.accessibility.reducedMotion)
        }
        state.value = next
    }

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            themeSource = enumOrDefault(prefs.getString(KEY_SOURCE, null), defaults.themeSource),
            themeMode = enumOrDefault(prefs.getString(KEY_MODE, null), defaults.themeMode),
            trueBlack = prefs.getBoolean(KEY_TRUE_BLACK, defaults.trueBlack),
            accent = if (prefs.contains(KEY_ACCENT)) prefs.getInt(KEY_ACCENT, 0) else null,
            defaultSubscriptionId = prefs.getInt(KEY_SUBSCRIPTION, defaults.defaultSubscriptionId),
            sendDelaySeconds = prefs.getInt(KEY_SEND_DELAY, defaults.sendDelaySeconds)
                .takeIf { it in SEND_DELAYS } ?: defaults.sendDelaySeconds,
            accessibility = defaults.accessibility.let { a ->
                Accessibility(
                    on = prefs.getBoolean(KEY_A11Y, a.on),
                    highContrast = prefs.getBoolean(KEY_A11Y_CONTRAST, a.highContrast),
                    largerText = prefs.getBoolean(KEY_A11Y_TEXT, a.largerText),
                    largerTargets = prefs.getBoolean(KEY_A11Y_TARGETS, a.largerTargets),
                    reducedMotion = prefs.getBoolean(KEY_A11Y_MOTION, a.reducedMotion),
                )
            },
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        private const val KEY_SOURCE = "theme_source"
        private const val KEY_MODE = "theme_mode"
        private const val KEY_TRUE_BLACK = "true_black"
        private const val KEY_ACCENT = "accent"
        private const val KEY_SUBSCRIPTION = "default_subscription"
        private const val KEY_SEND_DELAY = "send_delay_seconds"
        private const val KEY_A11Y = "accessibility"
        private const val KEY_A11Y_CONTRAST = "accessibility_contrast"
        private const val KEY_A11Y_TEXT = "accessibility_text"
        private const val KEY_A11Y_TARGETS = "accessibility_targets"
        private const val KEY_A11Y_MOTION = "accessibility_motion"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}

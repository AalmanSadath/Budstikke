package dev.aalman.budstikke.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.aalman.budstikke.MainActivity

/**
 * The name under the app's icon: "Budstikke", or "Messages". The manifest has the launcher
 * entry twice, as two aliases of the main screen with those labels, and exactly one is
 * enabled. The choice lives in the package manager rather than in settings, so it is read
 * back from there and survives clearing the app's data the way the icon itself does.
 */
object LauncherName {

    private val PREFIX = MainActivity::class.java.name.substringBeforeLast('.')

    private fun alias(context: Context, name: String) = ComponentName(context.packageName, "$PREFIX.$name")

    fun isMessages(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context, MESSAGES)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /**
     * Switches the launcher entry. The new one is enabled before the old one is disabled,
     * so there is never a moment with no way to open the app from the drawer. Launchers
     * treat the switch as one app removed and another added, and many take a pinned
     * home-screen icon away with the old entry.
     */
    fun set(context: Context, messages: Boolean) {
        val pm = context.packageManager
        val (on, off) = if (messages) MESSAGES to BUDSTIKKE else BUDSTIKKE to MESSAGES
        pm.setComponentEnabledSetting(alias(context, on), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(alias(context, off), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    private const val BUDSTIKKE = "LauncherBudstikke"
    private const val MESSAGES = "LauncherMessages"
}

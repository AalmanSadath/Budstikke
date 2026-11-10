package dev.aalman.budstikke.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/**
 * An active SIM that can send SMS. [name] is the name saved for it in Android's SIM settings,
 * which defaults to the carrier's; [slotLabel] is "SIM 1", "SIM 2" and so on.
 */
data class Sim(val subscriptionId: Int, val slotLabel: String, val name: String)

/**
 * The phone's active SIMs, in slot order. Empty when the app may not read phone state,
 * in which case sending goes through the system's default SIM.
 */
fun activeSims(context: Context): List<Sim> {
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
    return manager.activeSubscriptionInfoList.orEmpty()
        .sortedBy { it.simSlotIndex }
        .map {
            val slot = "SIM ${it.simSlotIndex + 1}"
            Sim(
                subscriptionId = it.subscriptionId,
                slotLabel = slot,
                name = it.displayName?.toString()?.takeIf { name -> name.isNotBlank() } ?: slot,
            )
        }
}

/**
 * The SIM a new message goes out on: the system's default SMS SIM when it is still active,
 * otherwise the first active SIM. Null means "let the system decide".
 */
fun preferredSim(sims: List<Sim>): Sim? {
    val default = SubscriptionManager.getDefaultSmsSubscriptionId()
    return sims.firstOrNull { it.subscriptionId == default } ?: sims.firstOrNull()
}

internal fun smsManagerFor(context: Context, subscriptionId: Int): SmsManager {
    val valid = subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(SmsManager::class.java)
        if (valid) manager.createForSubscriptionId(subscriptionId) else manager
    } else {
        @Suppress("DEPRECATION")
        if (valid) SmsManager.getSmsManagerForSubscriptionId(subscriptionId) else SmsManager.getDefault()
    }
}

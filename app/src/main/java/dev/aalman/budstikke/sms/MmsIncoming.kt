package dev.aalman.budstikke.sms

import android.content.Context
import android.provider.Telephony.Mms
import android.telephony.TelephonyManager
import android.util.Log
import dev.aalman.budstikke.data.BlockList
import dev.aalman.budstikke.notify.MessageNotifier
import dev.aalman.budstikke.settings.SettingsRepository

/**
 * Handles the push that says an MMS is waiting at the carrier: stores it, so the
 * conversation shows it, and downloads the message when the settings allow. Shared by
 * [MmsDeliverReceiver] and, in debug builds, the emulator's test receiver. Call off the main
 * thread.
 */
object MmsIncoming {

    fun handle(context: Context, pdu: ByteArray, subscriptionId: Int) {
        val app = context.applicationContext
        val ind = MmsPdu.notification(pdu)
        if (ind == null) {
            Log.w(TAG, "Unreadable MMS notification, ${pdu.size} bytes")
            return
        }
        val location = ind.contentLocation?.let { String(it) }?.takeIf { it.isNotBlank() } ?: return
        if (alreadyStored(app, location)) return
        val from = ind.from?.string?.let(::plainAddress) ?: return
        // A blocked sender's MMS waits silently in Spam & blocked, as read, and is not
        // fetched: downloading it would spend data on a message nobody asked for.
        val blocked = BlockList.get(app).isBlocked(from)
        val store = MmsStore(app)
        val id = store.insertNotification(ind, subscriptionId, read = blocked) ?: return
        if (blocked) return
        if (shouldDownload(app, subscriptionId)) {
            MmsDownloader.start(app, id)
        } else {
            store.threadOf(id)?.let { MessageNotifier(app).update(it) }
        }
    }

    /** Carriers send the same notification again when they hear nothing back. */
    private fun alreadyStored(context: Context, location: String): Boolean =
        context.contentResolver.query(
            Mms.CONTENT_URI, arrayOf(Mms._ID), "${Mms.CONTENT_LOCATION} = ?", arrayOf(location), null,
        )?.use { it.count > 0 } ?: false

    private fun shouldDownload(context: Context, subscriptionId: Int): Boolean {
        val settings = SettingsRepository.get(context).settings.value
        if (!settings.autoDownloadMms) return false
        if (settings.autoDownloadMmsRoaming) return true
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return true
        val roaming = runCatching { telephony.createForSubscriptionId(subscriptionId).isNetworkRoaming }.getOrDefault(false)
        return !roaming
    }

    private const val TAG = "MmsIncoming"
}

package dev.aalman.budstikke.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dev.aalman.budstikke.settings.SettingsRepository
import kotlin.concurrent.thread

/**
 * "Reply with message" on an incoming call. The dialer sends the caller's number as an
 * sms: or smsto: URI and the chosen reply as EXTRA_TEXT; the reply goes out on the SIM
 * chosen for new messages in settings.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipients = intent?.data?.let(::recipientsOf).orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE || recipients.isEmpty() || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        thread(name = "respond-via-message") {
            try {
                val sender = SmsSender(this)
                val chosen = SettingsRepository.get(this).settings.value.defaultSubscriptionId
                val subscription = preferredSim(activeSims(this), chosen)?.subscriptionId
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                recipients.forEach { sender.send(it, text, subscription) }
            } catch (e: Exception) {
                Log.e("RespondViaMessage", "Could not send reply", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}

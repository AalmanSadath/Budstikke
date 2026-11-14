package dev.aalman.budstikke.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * Receives WAP_PUSH_DELIVER, which Android sends only to the default SMS app, when the
 * carrier says an MMS is waiting. The push holds only a notification; [MmsIncoming] stores
 * it and fetches the message itself.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != MMS_MESSAGE_TYPE) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.getDefaultSmsSubscriptionId(),
        )
        val pending = goAsync()
        thread(name = "mms-deliver") {
            try {
                MmsIncoming.handle(context, pdu, subscriptionId)
            } catch (e: Exception) {
                Log.e("MmsDeliverReceiver", "Could not handle an MMS notification", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val MMS_MESSAGE_TYPE = "application/vnd.wap.mms-message"
    }
}

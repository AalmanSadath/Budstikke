package dev.aalman.budstikke.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * Receives SMS_DELIVER, which Android sends only to the default SMS app, and writes the
 * message to the system message store. As the default app, nothing else will store it: if
 * this insert does not happen, the message is lost.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sms = joinParts(
            messages.filterNotNull().map {
                SmsPart(it.displayOriginatingAddress, it.displayMessageBody, it.timestampMillis)
            },
        ) ?: return
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )

        val pending = goAsync()
        thread(name = "sms-deliver") {
            try {
                store(context, sms, subscriptionId)
            } catch (e: Exception) {
                Log.e(TAG, "Could not store incoming SMS", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun store(context: Context, sms: IncomingSms, subscriptionId: Int) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sms.address)
            put(Telephony.Sms.BODY, sms.body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sms.sentAtMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        // The provider assigns the thread from the address.
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}

package dev.aalman.budstikke.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import dev.aalman.budstikke.data.Locks
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import dev.aalman.budstikke.data.BlockList
import dev.aalman.budstikke.notify.MessageNotifier
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
        // A blocked sender's message is still kept, as read, so it waits silently in
        // Spam & blocked and nothing counts it as unread.
        val blocked = BlockList.get(context).isBlocked(sms.address)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sms.address)
            put(Telephony.Sms.BODY, sms.body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sms.sentAtMillis)
            put(Telephony.Sms.READ, if (blocked) 1 else 0)
            put(Telephony.Sms.SEEN, if (blocked) 1 else 0)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        // The provider assigns the thread from the address.
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return
        if (blocked) return
        Locks.get(context).coverReaction(uri, sms.body)
        val threadId = context.contentResolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return
        MessageNotifier(context).update(threadId)
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}

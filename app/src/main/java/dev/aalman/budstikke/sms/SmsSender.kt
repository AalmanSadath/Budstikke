package dev.aalman.budstikke.sms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import dev.aalman.budstikke.data.Locks
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log

/**
 * Sends SMS. The default SMS app has to record its own sent messages, so each one is first
 * written to the outbox, then handed to the radio; [SmsStatusReceiver] moves it to sent or
 * failed and records the delivery report when the carrier sends one.
 *
 * Every call does disk and radio work and must run off the main thread.
 */
class SmsSender(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    /** Sends [body] to [address]. Returns the stored message's URI, or null if it could not be stored. */
    fun send(
        address: String,
        body: String,
        subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    ): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        val uri = resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values) ?: return null
        Locks.get(context).coverReaction(uri, body)
        dispatch(uri, address, body, subscriptionId)
        return uri
    }

    /** Sends a failed message again, on the SIM it was first sent from. */
    fun retry(messageId: Long) {
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
        val row = resolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.SUBSCRIPTION_ID),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) null else Triple(c.getString(0), c.getString(1), c.getInt(2))
        } ?: return
        val (address, body, subscriptionId) = row
        resolver.update(
            uri,
            ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
            },
            null,
            null,
        )
        dispatch(uri, address, body, subscriptionId)
    }

    private fun dispatch(uri: Uri, address: String, body: String, subscriptionId: Int) {
        try {
            val manager = smsManagerFor(context, subscriptionId)
            val parts = manager.divideMessage(body)
            val sent = ArrayList(parts.indices.map { statusIntent(SmsStatusReceiver.ACTION_SENT, uri, it) })
            val delivered = ArrayList(parts.indices.map { statusIntent(SmsStatusReceiver.ACTION_DELIVERED, uri, it) })
            manager.sendMultipartTextMessage(address, null, parts, sent, delivered)
        } catch (e: Exception) {
            // Without a radio call there is no status callback, so the failure is recorded here.
            Log.e(TAG, "Could not send SMS", e)
            SmsStatusReceiver.markFailed(context, uri)
        }
    }

    /**
     * One PendingIntent per part and per report. The part number goes into the data URI so
     * the intents stay distinct; the telephony stack fills in the delivery PDU as extras,
     * which is why the intent has to be mutable. It names an unexported receiver in this
     * app, so nothing else can be targeted with it.
     */
    private fun statusIntent(action: String, message: Uri, part: Int): PendingIntent {
        val intent = Intent(action, message.buildUpon().appendQueryParameter("part", part.toString()).build())
            .setClass(context, SmsStatusReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    private companion object {
        const val TAG = "SmsSender"
    }
}

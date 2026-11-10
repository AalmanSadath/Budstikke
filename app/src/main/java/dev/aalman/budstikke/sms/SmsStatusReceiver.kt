package dev.aalman.budstikke.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlin.concurrent.thread

/** Records the radio's sent result and the carrier's delivery report for each sent SMS. */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.data?.buildUpon()?.clearQuery()?.build() ?: return
        val action = intent.action
        val result = resultCode
        val report = if (action == ACTION_DELIVERED) deliveryStatus(intent) else null

        val pending = goAsync()
        thread(name = "sms-status") {
            try {
                when (action) {
                    ACTION_SENT -> if (result == Activity.RESULT_OK) markSent(context, message) else markFailed(context, message)
                    ACTION_DELIVERED -> report?.let { setStatus(context, message, it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not record SMS status", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** TP-Status from the report PDU, reduced to the store's three delivery states. */
    private fun deliveryStatus(intent: Intent): Int? {
        val pdu = intent.getByteArrayExtra("pdu") ?: return null
        val format = intent.getStringExtra("format")
        val status = SmsMessage.createFromPdu(pdu, format)?.status ?: return null
        return deliveryStatusOf(status)
    }

    companion object {
        const val ACTION_SENT = "dev.aalman.budstikke.SMS_SENT"
        const val ACTION_DELIVERED = "dev.aalman.budstikke.SMS_DELIVERED"
        private const val TAG = "SmsStatusReceiver"

        /**
         * Moves the message from the outbox to sent. A long message reports once per part,
         * so a part that already failed keeps the whole message failed.
         */
        private fun markSent(context: Context, message: Uri) {
            context.contentResolver.update(
                message,
                ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) },
                "${Telephony.Sms.TYPE} != ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString()),
            )
        }

        internal fun markFailed(context: Context, message: Uri) {
            context.contentResolver.update(
                message,
                ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) },
                null,
                null,
            )
        }

        private fun setStatus(context: Context, message: Uri, status: Int) {
            context.contentResolver.update(
                message,
                ContentValues().apply { put(Telephony.Sms.STATUS, status) },
                null,
                null,
            )
        }
    }
}

/**
 * TP-Status (3GPP TS 23.040, 9.2.3.15): below 0x20 the message was delivered, 0x20 to 0x3F
 * the carrier is still trying, and from 0x40 it gave up.
 */
internal fun deliveryStatusOf(tpStatus: Int): Int = when {
    tpStatus < 0x20 -> Telephony.Sms.STATUS_COMPLETE
    tpStatus < 0x40 -> Telephony.Sms.STATUS_PENDING
    else -> Telephony.Sms.STATUS_FAILED
}

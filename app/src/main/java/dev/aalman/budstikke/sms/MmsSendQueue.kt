package dev.aalman.budstikke.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Mms
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Hands PDUs to Android's MMS service to send. [ACTION_SENT] comes back to
 * [MmsResultReceiver] with the service's result, which moves the message to sent or failed.
 */
object MmsSendQueue {

    /**
     * Sends [bytes] on [subscriptionId]. [mmsId] is the message's row in the store, or -1 for
     * a PDU that has none, such as the acknowledgement of a download.
     */
    fun sendRaw(context: Context, bytes: ByteArray, subscriptionId: Int, mmsId: Long = -1): Boolean {
        val app = context.applicationContext
        val file = File(MmsFiles.dir(app), "send-${UUID.randomUUID()}.pdu")
        val uri = MmsFiles.uri(app, file)
        return try {
            file.writeBytes(bytes)
            MmsFiles.grant(app, uri, write = false)
            val result = Intent(ACTION_SENT, uri)
                .setClass(app, MmsResultReceiver::class.java)
                .putExtra(EXTRA_MMS_ID, mmsId)
            // Mutable so the service can add its result; it names this app's unexported
            // receiver, so nothing else can be sent with it.
            val pending = PendingIntent.getBroadcast(app, file.name.hashCode(), result, PendingIntent.FLAG_MUTABLE)
            smsManagerFor(app, subscriptionId).sendMultimediaMessage(app, uri, null, null, pending)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not hand the MMS to the MMS service", e)
            MmsFiles.revoke(app, uri)
            file.delete()
            if (mmsId >= 0) MmsStore(app).setBox(mmsId, Mms.MESSAGE_BOX_FAILED)
            false
        }
    }

    internal fun finish(context: Context, mmsId: Long, resultCode: Int, uri: Uri) {
        val app = context.applicationContext
        MmsFiles.revoke(app, uri)
        uri.lastPathSegment?.let { File(MmsFiles.dir(app), it).delete() }
        if (mmsId < 0) return
        val box = if (resultCode == Activity.RESULT_OK) Mms.MESSAGE_BOX_SENT else Mms.MESSAGE_BOX_FAILED
        if (box == Mms.MESSAGE_BOX_FAILED) Log.w(TAG, "MMS send failed: result $resultCode")
        MmsStore(app).setBox(mmsId, box)
    }

    const val ACTION_SENT = "dev.aalman.budstikke.MMS_SENT"
    const val EXTRA_MMS_ID = "mms_id"
    private const val TAG = "MmsSendQueue"
}

package dev.aalman.budstikke.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.CarrierConfigManager
import android.util.Log
import dev.aalman.budstikke.data.DownloadState
import dev.aalman.budstikke.data.MmsDownloads
import dev.aalman.budstikke.mms.pdu.NotifyRespInd
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.notify.MessageNotifier
import java.io.File
import kotlin.concurrent.thread

/**
 * Fetches a waiting MMS through Android's MMS service, which downloads it over mobile data
 * into a file this app then reads. Call [start] off the main thread.
 */
object MmsDownloader {

    fun start(context: Context, mmsId: Long) {
        val app = context.applicationContext
        val waiting = MmsStore(app).waiting(mmsId) ?: return
        val downloads = MmsDownloads.get(app)
        val file = File(MmsFiles.dir(app), "download-$mmsId.pdu").apply { delete() }
        val uri = MmsFiles.uri(app, file)
        downloads.set(mmsId, DownloadState.Downloading)
        try {
            file.createNewFile()
            MmsFiles.grant(app, uri, write = true)
            val result = Intent(ACTION_DOWNLOADED, uri)
                .setClass(app, MmsResultReceiver::class.java)
                .putExtra(EXTRA_MMS_ID, mmsId)
            // Mutable so the service can add its result; it names this app's unexported
            // receiver, so nothing else can be sent with it.
            val pending = PendingIntent.getBroadcast(app, mmsId.toInt(), result, PendingIntent.FLAG_MUTABLE)
            smsManagerFor(app, waiting.subscriptionId)
                .downloadMultimediaMessage(app, waiting.location, uri, null, pending)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the MMS download", e)
            MmsFiles.revoke(app, uri)
            file.delete()
            downloads.set(mmsId, DownloadState.Failed)
        }
    }

    /**
     * The service's answer: reads the downloaded file, writes the message into the store in
     * place of its waiting row, and notifies. A download that fails keeps the waiting row,
     * marked failed, so it can be tried again.
     */
    internal fun finish(context: Context, mmsId: Long, resultCode: Int, uri: android.net.Uri) {
        val app = context.applicationContext
        val downloads = MmsDownloads.get(app)
        val store = MmsStore(app)
        val file = File(MmsFiles.dir(app), "download-$mmsId.pdu")
        MmsFiles.revoke(app, uri)
        try {
            val waiting = store.waiting(mmsId) ?: return
            val bytes = if (resultCode == Activity.RESULT_OK && file.length() in 1..MmsPdu.MAX_PDU_BYTES) file.readBytes() else null
            val conf = bytes?.let { MmsPdu.retrieveConf(it) }
            if (conf == null) {
                Log.w(TAG, "MMS download failed: result $resultCode, ${file.length()} bytes")
                downloads.set(mmsId, DownloadState.Failed)
                return
            }
            val record = MmsStore.fromRetrieveConf(conf, waiting.subscriptionId, read = false)
            val newId = store.insert(record)
            if (newId == null) {
                downloads.set(mmsId, DownloadState.Failed)
                return
            }
            store.delete(listOf(mmsId))
            downloads.forget(listOf(mmsId))
            acknowledge(app, waiting)
            val threadId = store.threadOf(newId) ?: waiting.threadId
            MessageNotifier(app).update(threadId)
        } finally {
            file.delete()
        }
    }

    /**
     * Tells the carrier the message arrived, when its configuration asks for that, so it
     * does not keep the message waiting or send the notification again.
     */
    private fun acknowledge(context: Context, waiting: MmsStore.Waiting) {
        // Reading carrier settings needs READ_PHONE_STATE, which the app asks for with the SMS
        // role; without it, no acknowledgement is sent.
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val config = try {
            context.getSystemService(CarrierConfigManager::class.java)?.getConfigForSubId(waiting.subscriptionId)
        } catch (_: SecurityException) {
            null
        }
        if (config?.getBoolean(CarrierConfigManager.KEY_MMS_NOTIFY_WAP_MMSC_ENABLED_BOOL) != true) return
        val transactionId = waiting.transactionId ?: return
        val pdu = runCatching {
            NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION, transactionId.toByteArray(), PduHeaders.STATUS_RETRIEVED)
        }.getOrNull() ?: return
        val bytes = MmsPdu.compose(context, pdu) ?: return
        MmsSendQueue.sendRaw(context, bytes, waiting.subscriptionId)
    }

    const val ACTION_DOWNLOADED = "dev.aalman.budstikke.MMS_DOWNLOADED"
    const val EXTRA_MMS_ID = "mms_id"
    private const val TAG = "MmsDownloader"
}

/** Results from Android's MMS service, for downloads and sends. Not exported. */
class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val code = resultCode
        val pending = goAsync()
        thread(name = "mms-result") {
            try {
                when (intent.action) {
                    MmsDownloader.ACTION_DOWNLOADED ->
                        MmsDownloader.finish(context, intent.getLongExtra(MmsDownloader.EXTRA_MMS_ID, -1), code, uri)
                    MmsSendQueue.ACTION_SENT ->
                        MmsSendQueue.finish(context, intent.getLongExtra(MmsSendQueue.EXTRA_MMS_ID, -1), code, uri)
                }
            } catch (e: Exception) {
                Log.e("MmsResultReceiver", "Could not handle an MMS result", e)
            } finally {
                pending.finish()
            }
        }
    }
}

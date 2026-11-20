package dev.aalman.budstikke.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import dev.aalman.budstikke.sms.fitWithin
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Small copies of received pictures for message notifications. The original never leaves
 * the message store: each copy is decoded at most [MAX_SIDE] pixels on its longer edge,
 * with the size set before any pixels are read, re-encoded as JPEG, which also drops its
 * metadata, and handed out by this app's FileProvider. Android lets its system UI read a
 * picture in a messaging notification for as long as the notification shows.
 *
 * Copies live in a folder per conversation, named by message, so they go when the
 * conversation's notification does. Call off the main thread.
 */
internal object NotificationImages {

    const val MAX_SIDE = 1024
    private const val QUALITY = 85
    private val KEEP_MILLIS = TimeUnit.DAYS.toMillis(7)

    /** The scaled copy of the picture at [source] for message [rowId], made once; null when it cannot be read. */
    fun forMessage(context: Context, threadId: Long, rowId: Long, source: Uri): Uri? {
        val file = File(folder(context, threadId), "$rowId.jpg")
        if (!file.exists() && !write(context, source, file)) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Drops the conversation's copies other than those of [rowIds], the messages still shown. */
    fun keepOnly(context: Context, threadId: Long, rowIds: Set<Long>) {
        folder(context, threadId).listFiles()?.forEach { if (it.nameWithoutExtension.toLongOrNull() !in rowIds) it.delete() }
    }

    /** Drops every copy of the conversation's, once its notification is gone. */
    fun clear(context: Context, threadId: Long) {
        folder(context, threadId).deleteRecursively()
    }

    /** Drops copies left behind longer than any notification stays, at app start. */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()) {
        File(context.cacheDir, "notify").listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { if (now - it.lastModified() > KEEP_MILLIS) it.delete() }
            if (dir.list().isNullOrEmpty()) dir.delete()
        }
    }

    private fun folder(context: Context, threadId: Long) = File(File(context.cacheDir, "notify"), threadId.toString())

    private fun write(context: Context, source: Uri, file: File): Boolean {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
                fitWithin(info.size.width, info.size.height, MAX_SIDE)?.let { (w, h) -> decoder.setTargetSize(w, h) }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return false
        return try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.part")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            temp.renameTo(file)
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }
}

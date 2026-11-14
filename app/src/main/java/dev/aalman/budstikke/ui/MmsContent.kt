package dev.aalman.budstikke.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.Attachment
import dev.aalman.budstikke.data.AttachmentKind
import dev.aalman.budstikke.data.DownloadState
import dev.aalman.budstikke.data.WaitingMms
import dev.aalman.budstikke.data.attachmentLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The pictures and files of an MMS, above its text in the bubble. Pictures show inside the
 * bubble's shape; anything else is a chip that opens it in another app.
 */
@Composable
internal fun AttachmentsContent(
    attachments: List<Attachment>,
    outgoing: Boolean,
    onOpenImage: (Attachment) -> Unit,
    /** A long press selects the message, as it does anywhere else on the bubble. */
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        attachments.forEach { a ->
            if (a.kind == AttachmentKind.Image) {
                BubbleImage(a, onClick = { onOpenImage(a) }, onLongClick = onLongClick)
            } else {
                AttachmentChip(a, outgoing, onClick = { openAttachment(context, a) }, onLongClick = onLongClick)
            }
        }
    }
}

@Composable
private fun BubbleImage(attachment: Attachment, onClick: () -> Unit, onLongClick: () -> Unit) {
    val image = rememberDecoded(attachment.uri, BUBBLE_IMAGE_PX)
    val label = stringResource(R.string.attachment_photo)
    val shape = RoundedCornerShape(18.dp)
    val bitmap = image
    if (bitmap == null) {
        Box(
            Modifier
                .size(200.dp, 150.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clearAndSetSemantics { contentDescription = label },
        )
        return
    }
    // Sized to the picture's own shape, so the rounded corners are the picture's corners.
    Image(
        bitmap,
        contentDescription = label,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .heightIn(max = 360.dp)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
    )
}

@Composable
private fun AttachmentChip(attachment: Attachment, outgoing: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val label = attachment.name ?: stringResource(attachmentLabel(attachment.kind))
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Icon(painterResource(R.drawable.ic_attach_file), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontSize = 15.sp, maxLines = 1)
    }
}

/** An MMS still at the carrier: what it is, and the button that fetches it. */
@Composable
internal fun WaitingContent(waiting: WaitingMms, onDownload: () -> Unit) {
    val context = LocalContext.current
    val size = waiting.sizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (waiting.download == DownloadState.Downloading) Modifier else Modifier.clickable(onClick = onDownload))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_image), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mms_waiting), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.size(6.dp))
        when (waiting.download) {
            DownloadState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mms_downloading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DownloadState.Failed -> Text(
                stringResource(R.string.mms_download_failed), fontSize = 14.sp, color = MaterialTheme.colorScheme.error,
            )
            DownloadState.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_download), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (size != null) stringResource(R.string.mms_download_size, size) else stringResource(R.string.mms_download),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** One picture over the whole screen, fitted; tap the close button or go back to leave. */
@Composable
internal fun PhotoViewer(attachment: Attachment, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val image = rememberDecoded(attachment.uri, VIEWER_IMAGE_PX)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            image?.let {
                Image(
                    it,
                    contentDescription = stringResource(R.string.attachment_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.photo_viewer_close), tint = Color.White)
            }
        }
    }
}

/**
 * Decodes a picture off the main thread, scaled so its longest side is at most [maxPx]. The
 * bytes come from whoever sent the MMS, so the size is set before decoding: a picture that
 * claims huge dimensions cannot make the app allocate for them.
 */
@Composable
private fun rememberDecoded(uri: Uri, maxPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri, maxPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val longest = maxOf(w, h)
                    if (longest > maxPx) {
                        val scale = maxPx.toFloat() / longest
                        decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    return image
}

/**
 * Opens a video, sound, contact card or file in another app. The part is copied into this
 * app's cache and handed out through its FileProvider with a read grant on that one URI,
 * since the receiving app cannot read the message store itself.
 */
internal fun openAttachment(context: Context, attachment: Attachment) {
    val app = context.applicationContext
    val dir = File(app.cacheDir, "share").apply { mkdirs() }
    // Only a plain file name from the sender is kept; anything else gets a generic one.
    val name = attachment.name?.substringAfterLast('/')?.takeIf { it.matches(Regex("[\\w .()-]{1,80}")) } ?: "attachment"
    Thread {
        val file = File(dir, name)
        val copied = runCatching {
            app.contentResolver.openInputStream(attachment.uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        }.getOrNull()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (copied == null) {
                Toast.makeText(context, R.string.attachment_open_none, Toast.LENGTH_SHORT).show()
                return@post
            }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, attachment.contentType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.attachment_open_none, Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

private const val BUBBLE_IMAGE_PX = 1080
private const val VIEWER_IMAGE_PX = 2560

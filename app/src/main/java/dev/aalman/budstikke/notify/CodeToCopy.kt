package dev.aalman.budstikke.notify

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dev.aalman.budstikke.R
import dev.aalman.budstikke.sms.findOneTimeCode

/** A one-time code a notification offers to copy, and the message it came from. */
data class CodeToCopy(val messageId: Long, val code: String)

/**
 * The code for a notification's Copy code button: from the newest of [messages] (id and
 * text, oldest first) that holds one and is not locked, or null when none does. A locked
 * message's text is hidden from the shade, so its code is not offered either.
 */
fun codeToCopy(messages: List<Pair<Long, String>>, isLocked: (Long) -> Boolean): CodeToCopy? =
    messages.asReversed().firstNotNullOfOrNull { (id, body) ->
        if (isLocked(id)) null else findOneTimeCode(body)?.let { CodeToCopy(id, it.code) }
    }

/**
 * Puts a one-time code on the clipboard, marked sensitive so that Android 13 and later keep
 * it out of the clipboard preview and keyboard suggestions.
 */
fun copyCode(context: Context, code: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.one_time_code), code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

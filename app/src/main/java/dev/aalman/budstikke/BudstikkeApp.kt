package dev.aalman.budstikke

import android.app.Application
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import dev.aalman.budstikke.data.Trash
import dev.aalman.budstikke.trash.schedulePurge
import kotlin.concurrent.thread

class BudstikkeApp : Application() {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        // The message field adds SIM choices to its long-press menu, which only the newer
        // text context menu supports.
        ComposeFoundationFlags.isNewContextMenuEnabled = true
        // Empty expired trash whenever the app's process starts, and make sure the daily
        // alarm that does it otherwise is set.
        schedulePurge(this, onlyIfMissing = true)
        thread(name = "trash-purge") {
            runCatching { Trash.get(this).purgeExpired() }
        }
    }
}

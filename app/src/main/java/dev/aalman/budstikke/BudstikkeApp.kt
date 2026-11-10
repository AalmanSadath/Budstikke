package dev.aalman.budstikke

import android.app.Application
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi

class BudstikkeApp : Application() {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        // The message field adds SIM choices to its long-press menu, which only the newer
        // text context menu supports.
        ComposeFoundationFlags.isNewContextMenuEnabled = true
    }
}

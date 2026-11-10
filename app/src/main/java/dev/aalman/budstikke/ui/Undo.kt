package dev.aalman.budstikke.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recent actions that can be undone, offered one at a time in a snackbar, newest first.
 * Undoing one offers the one before it; letting the snackbar time out forgets them all.
 * Lives above every screen, so an action taken on one screen can be undone on the next.
 */
class UndoQueue(
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
    private val undoLabel: String,
) {
    private class Entry(val message: String, val undo: () -> Unit)

    private val stack = ArrayDeque<Entry>()
    private var showing: Job? = null

    /** Offers [undo], which runs off the main thread, under [message]. */
    fun push(message: String, undo: () -> Unit) {
        stack.addLast(Entry(message, undo))
        showTop()
    }

    private fun showTop() {
        showing?.cancel()
        val top = stack.lastOrNull() ?: return
        showing = scope.launch {
            val result = snackbar.showSnackbar(top.message, undoLabel, duration = SnackbarDuration.Short)
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    stack.remove(top)
                    // A newer push cancels this job; the undo already started must finish.
                    withContext(NonCancellable + Dispatchers.IO) { top.undo() }
                    showTop()
                }
                SnackbarResult.Dismissed -> stack.clear()
            }
        }
    }
}

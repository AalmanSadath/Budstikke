package dev.aalman.budstikke.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where the one backup, restore or import running at a time stands. */
sealed interface JobState {
    data object Idle : JobState
    data class Running(val kind: JobKind, val done: Int, val total: Int) : JobState
    data class BackedUp(val messages: Int) : JobState
    data class Restored(val result: RestoreResult) : JobState
    data class Failed(val kind: JobKind, val reason: FailReason) : JobState
}

enum class JobKind { Backup, Restore }

enum class FailReason { WrongPin, NotABackup, File }

/**
 * Runs backups and restores for the life of the process rather than of a screen, so turning
 * the phone or leaving settings does not stop one halfway. Only one runs at a time.
 */
object BackupJobs {

    private const val TAG = "BackupJobs"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state

    fun backup(context: Context, uri: Uri, pin: CharArray) = run(JobKind.Backup, pin) { progress ->
        JobState.BackedUp(Backup(context).write(uri, pin, progress))
    }

    fun restore(context: Context, uri: Uri, pin: CharArray) = run(JobKind.Restore, pin) { progress ->
        JobState.Restored(Backup(context).restore(uri, pin, progress))
    }

    /** Clears a finished job's result once it has been shown. */
    fun dismiss() {
        if (_state.value !is JobState.Running) _state.value = JobState.Idle
    }

    private fun run(kind: JobKind, pin: CharArray, work: (Progress) -> JobState) {
        if (_state.value is JobState.Running) return
        _state.value = JobState.Running(kind, 0, 0)
        scope.launch {
            _state.value = try {
                work { done, total -> _state.value = JobState.Running(kind, done, total) }
            } catch (_: WrongPinException) {
                JobState.Failed(kind, FailReason.WrongPin)
            } catch (_: NotABackupException) {
                JobState.Failed(kind, FailReason.NotABackup)
            } catch (e: Exception) {
                Log.e(TAG, "$kind failed", e)
                JobState.Failed(kind, FailReason.File)
            } finally {
                pin.fill('\u0000')
            }
        }
    }
}

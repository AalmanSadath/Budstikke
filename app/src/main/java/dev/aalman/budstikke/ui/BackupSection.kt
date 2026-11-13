package dev.aalman.budstikke.ui

import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.aalman.budstikke.R
import dev.aalman.budstikke.backup.BackupCrypto
import dev.aalman.budstikke.backup.BackupJobs
import dev.aalman.budstikke.backup.FailReason
import dev.aalman.budstikke.backup.ImportSource
import dev.aalman.budstikke.backup.JobKind
import dev.aalman.budstikke.backup.JobState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the PIN dialog is open for. */
private enum class PinFor { Backup, BackupTo, Restore }

/**
 * Settings' backup rows. Backing up asks for a PIN, then where to save; restoring asks
 * which file, then its PIN. The PIN is held only in memory, never in saved screen state,
 * and is wiped once the job has used it.
 */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    // The PIN typed for a backup, waiting for the file picker to return.
    var heldPin by remember { mutableStateOf<CharArray?>(null) }
    var pinFor by rememberSaveable { mutableStateOf<PinFor?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pin = heldPin
        heldPin = null
        when {
            uri == null -> pin?.fill('\u0000')
            pin != null -> BackupJobs.backup(context, uri, pin)
            // The screen was rebuilt while the picker was open and the PIN went with it.
            else -> {
                pendingUri = uri.toString()
                pinFor = PinFor.BackupTo
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri.toString()
            pinFor = PinFor.Restore
        }
    }
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    var importSource by rememberSaveable { mutableStateOf<ImportSource?>(null) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val source = importSource
        importSource = null
        if (uri != null && source != null) BackupJobs.import(context, uri, source)
    }

    SectionHeading(stringResource(R.string.settings_backup))
    LinkRow(stringResource(R.string.backup_messages)) { pinFor = PinFor.Backup }
    LinkRow(stringResource(R.string.restore_backup)) {
        openFile.launch(arrayOf("*/*"))
    }
    LinkRow(stringResource(R.string.import_messages)) { choosingSource = true }
    if (choosingSource) {
        SourceDialog(
            onDismiss = { choosingSource = false },
            onPick = { source ->
                choosingSource = false
                if (source == null) {
                    openFile.launch(arrayOf("*/*"))
                } else {
                    importSource = source
                    openFile(importFile)
                }
            },
        )
    }

    when (pinFor) {
        PinFor.Backup, PinFor.BackupTo -> PinDialog(
            title = stringResource(R.string.backup_pin_title),
            hint = stringResource(R.string.backup_pin_hint),
            confirm = true,
            action = stringResource(if (pinFor == PinFor.Backup) R.string.backup_choose_file else R.string.backup_messages),
            onDismiss = {
                pinFor = null
                pendingUri = null
            },
            onDone = { pin ->
                val target = pendingUri
                pinFor = null
                pendingUri = null
                if (target != null) {
                    BackupJobs.backup(context, target.toUri(), pin)
                } else {
                    heldPin = pin
                    createFile.launch("Budstikke backup ${SimpleDateFormat("yyyy-MM-dd HHmm", Locale.ROOT).format(Date())}.budstikke")
                }
            },
        )
        PinFor.Restore -> PinDialog(
            title = stringResource(R.string.restore_pin_title),
            hint = null,
            confirm = false,
            action = stringResource(R.string.backup_restore),
            onDismiss = {
                pinFor = null
                pendingUri = null
            },
            onDone = { pin ->
                val target = pendingUri
                pinFor = null
                pendingUri = null
                if (target != null) BackupJobs.restore(context, target.toUri(), pin) else pin.fill('\u0000')
            },
        )
        null -> Unit
    }

    JobDialog()
}

@Composable
private fun PinDialog(
    title: String,
    hint: String?,
    confirm: Boolean,
    action: String,
    onDismiss: () -> Unit,
    onDone: (CharArray) -> Unit,
) {
    // Deliberately not saved with the screen's state, which Android may write to disk.
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val long = pin.length >= BackupCrypto.MIN_PIN_LENGTH
    val same = !confirm || pin == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) {
                    Text(hint, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                }
                val options = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                OutlinedTextField(
                    pin, { pin = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.backup_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = options,
                    supportingText = if (confirm && pin.isNotEmpty() && !long) {
                        { Text(stringResource(R.string.backup_pin_short)) }
                    } else {
                        null
                    },
                )
                if (confirm) {
                    OutlinedTextField(
                        again, { again = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.backup_pin_again)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = options,
                        supportingText = if (again.isNotEmpty() && !same) {
                            { Text(stringResource(R.string.backup_pin_mismatch)) }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        confirmButton = {
            // A restore takes any PIN: an older backup may have used one this app no longer allows.
            TextButton(onClick = { onDone(pin.toCharArray()) }, enabled = (long || !confirm) && pin.isNotEmpty() && same) {
                Text(action)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Any file type: other apps' backups come as .zip, .json or .ndjson, often with a vague type. */
private fun openFile(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) = launcher.launch(arrayOf("*/*"))

/** Where to import from. Null stands for a Budstikke backup, which is a restore. */
@Composable
private fun SourceDialog(onDismiss: () -> Unit, onPick: (ImportSource?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_messages)) },
        text = {
            Column {
                listOf(
                    null to (R.string.import_source_budstikke to R.string.import_source_budstikke_hint),
                    ImportSource.SmsImportExport to (R.string.import_source_sms_ie to R.string.import_source_sms_ie_hint),
                    ImportSource.Qksms to (R.string.import_source_qksms to R.string.import_source_qksms_hint),
                ).forEach { (source, texts) ->
                    LinkRow(stringResource(texts.first), stringResource(texts.second)) { onPick(source) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Progress while a backup or restore runs, then what came of it. */
@Composable
private fun JobDialog() {
    val state by BackupJobs.state.collectAsState()
    when (val s = state) {
        JobState.Idle -> Unit
        is JobState.Running -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = {
                Text(
                    stringResource(
                        when (s.kind) {
                            JobKind.Backup -> R.string.backup_running
                            JobKind.Restore -> R.string.restore_running
                            JobKind.Import -> R.string.import_running
                        },
                    ),
                )
            },
            text = {
                Column {
                    if (s.total > 0) {
                        LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.job_progress, s.done, s.total), fontSize = 13.sp)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.job_keep_open), fontSize = 13.sp)
                }
            },
            confirmButton = {},
        )
        is JobState.BackedUp -> ResultDialog(
            stringResource(R.string.backup_done_title),
            pluralStringResource(R.plurals.backup_done, s.messages, s.messages),
        )
        is JobState.Restored -> {
            val past = s.result.scheduledPast
            ResultDialog(
                stringResource(R.string.restore_done_title),
                stringResource(R.string.restore_done, s.result.added, s.result.alreadyHere) +
                    if (past > 0) "\n\n" + pluralStringResource(R.plurals.restore_scheduled_past, past, past) else "",
            )
        }
        is JobState.Imported -> {
            val r = s.result
            ResultDialog(
                stringResource(R.string.import_done_title),
                listOfNotNull(
                    stringResource(R.string.restore_done, r.added, r.alreadyHere),
                    r.mmsSkipped.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_mms_skipped, it, it) },
                    r.unreadable.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_unreadable, it, it) },
                ).joinToString("\n\n"),
            )
        }
        is JobState.Failed -> ResultDialog(
            stringResource(
                when (s.kind) {
                    JobKind.Backup -> R.string.backup_failed_title
                    JobKind.Restore -> R.string.restore_failed_title
                    JobKind.Import -> R.string.import_failed_title
                },
            ),
            stringResource(
                when (s.reason) {
                    FailReason.WrongPin -> R.string.restore_wrong_pin
                    FailReason.NotABackup -> if (s.kind == JobKind.Import) R.string.import_not_backup else R.string.restore_not_backup
                    FailReason.TooLarge -> R.string.import_too_large
                    FailReason.File -> when (s.kind) {
                        JobKind.Backup -> R.string.backup_file_error
                        else -> R.string.restore_file_error
                    }
                },
            ),
        )
    }
}

@Composable
private fun ResultDialog(title: String, text: String) {
    AlertDialog(
        onDismissRequest = BackupJobs::dismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = BackupJobs::dismiss) { Text(stringResource(R.string.ok)) } },
    )
}

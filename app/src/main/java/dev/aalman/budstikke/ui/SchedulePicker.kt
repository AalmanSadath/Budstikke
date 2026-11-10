package dev.aalman.budstikke.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aalman.budstikke.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Picks when a message goes out: Material 3's date picker, then its time picker, the clock
 * dial with a keyboard toggle for typing the time. Days before today cannot be picked, and
 * a time already past keeps the time picker open with a note.
 *
 * [initialMillis] is the time to start from, such as a scheduled message's current time;
 * without it the picker starts at the next full hour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePicker(initialMillis: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = remember(initialMillis) {
        Instant.ofEpochMilli(initialMillis ?: nextFullHour(System.currentTimeMillis(), zone)).atZone(zone)
    }
    // The date picker works in UTC midnights.
    val todayUtc = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    var date by rememberSaveable { mutableStateOf<Long?>(null) }

    val pickedDate = date
    if (pickedDate == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                override fun isSelectableYear(year: Int) = year >= LocalDate.now(zone).year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { date = dateState.selectedDateMillis },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(dateState)
        }
        return
    }

    val context = LocalContext.current
    val timeState = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    var inPast by remember { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { TimePickerDialogDefaults.Title(displayMode = mode) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = mode,
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton(onClick = {
                val day = Instant.ofEpochMilli(pickedDate).atZone(ZoneOffset.UTC).toLocalDate()
                val millis = scheduleMillis(day, timeState.hour, timeState.minute, zone)
                if (millis <= System.currentTimeMillis()) inPast = true else onPick(millis)
            }) { Text(stringResource(R.string.ok)) }
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(timeState) else TimeInput(timeState)
        if (inPast) {
            Text(
                stringResource(R.string.schedule_in_past),
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The moment [hour]:[minute] on [day] happens in [zone]. */
internal fun scheduleMillis(day: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
    day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

/** The start of the hour after [millis] on the clock in [zone], where the picker opens by default. */
internal fun nextFullHour(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()

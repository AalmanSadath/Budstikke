package dev.aalman.budstikke.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Calendar

/** Conversation list: time for today, weekday within the last week, date otherwise. */
fun listTime(context: Context, millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    return when {
        DateUtils.isToday(millis) -> DateFormat.getTimeFormat(context).format(millis)
        now.timeInMillis - millis < DateUtils.WEEK_IN_MILLIS ->
            DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
            DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH)
        else ->
            DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

/** Under a message: the time for today, otherwise the date and the time. */
fun messageTime(context: Context, millis: Long): String {
    val time = DateFormat.getTimeFormat(context).format(millis)
    return if (DateUtils.isToday(millis)) time else "${listTime(context, millis)}, $time"
}

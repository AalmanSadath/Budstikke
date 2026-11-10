package dev.aalman.budstikke.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.Conversation
import java.util.Calendar

@Composable
fun ConversationListScreen(conversations: List<Conversation>?) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.app_name))
        when {
            conversations == null -> Unit
            conversations.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(R.string.list_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                items(conversations, key = { it.threadId }) { ConversationRow(it) }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation) {
    val unread = conversation.unreadCount > 0
    val unreadLabel = if (unread) {
        pluralStringResource(R.plurals.unread_count, conversation.unreadCount, conversation.unreadCount)
    } else {
        ""
    }
    val snippet = if (conversation.lastFromMe) {
        stringResource(R.string.you_prefix, conversation.snippet)
    } else {
        conversation.snippet
    }
    val time = formatTime(conversation.dateMillis)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(conversation.address, unreadLabel, snippet, time)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conversation.address)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.address,
                    Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    time,
                    fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snippet,
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(conversation.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Avatar colours. Picked from the sender so a conversation keeps its colour. */
private val avatarColours = listOf(
    Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B),
    Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFF6366F1),
)

@Composable
private fun Avatar(address: String) {
    val initial = address.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val colour = avatarColours[Math.floorMod(address.hashCode(), avatarColours.size)]
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(colour),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Time for today, weekday within the last week, date otherwise. */
@Composable
private fun formatTime(millis: Long): String {
    val context = LocalContext.current
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

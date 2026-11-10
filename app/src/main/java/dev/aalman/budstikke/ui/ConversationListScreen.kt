package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

@Composable
fun ConversationListScreen(
    conversations: List<Conversation>?,
    onOpen: (Conversation) -> Unit,
    onNewMessage: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.app_name)) {
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
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
                // Room under the last row so the new-message button does not cover it.
                contentPadding = WindowInsets.navigationBars.add(WindowInsets(bottom = 88.dp)).asPaddingValues(),
            ) {
                items(conversations, key = { it.threadId }) { ConversationRow(it, onOpen) }
            }
        }
    }
        FloatingActionButton(
            onClick = onNewMessage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.new_message))
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onOpen: (Conversation) -> Unit) {
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
    val time = listTime(LocalContext.current, conversation.dateMillis)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(conversation) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(conversation.title, unreadLabel, snippet, time)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conversation.address, conversation.contact)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
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

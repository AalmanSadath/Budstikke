package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * The main list, or the archive when [archive] is true. Long-pressing a row starts selecting;
 * while anything is selected, taps add or remove rows and the top bar offers what can be
 * done to them.
 */
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>?,
    archive: Boolean,
    onOpen: (Conversation) -> Unit,
    onPin: (Set<Long>, Boolean) -> Unit,
    onArchive: (Set<Long>, Boolean) -> Unit,
    onDelete: (List<Conversation>) -> Unit,
    onBlock: (List<Conversation>) -> Unit,
    onBack: (() -> Unit)? = null,
    onNewMessage: (() -> Unit)? = null,
    onShowArchive: (() -> Unit)? = null,
    onShowTrash: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Long>()) }
    // Rows can leave the list while selected (archived elsewhere, or deleted by another app).
    LaunchedEffect(conversations) {
        val present = conversations?.mapTo(HashSet()) { it.threadId } ?: return@LaunchedEffect
        if (!present.containsAll(selected)) selected = selected intersect present
    }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    val tint = MaterialTheme.colorScheme.onSurface

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            val chosen = conversations.orEmpty().filter { it.threadId in selected }
            TopBar(
                pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                leading = {
                    IconButton(onClick = { selected = emptySet() }, modifier = Modifier.size(40.dp)) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_selection), tint = tint)
                    }
                },
            ) {
                // Left of the rest, so adding it moves none of the buttons that were there.
                BarAction(R.drawable.ic_block, R.string.block_number) {
                    onBlock(chosen)
                    selected = emptySet()
                }
                BarAction(
                    if (archive) R.drawable.ic_unarchive else R.drawable.ic_archive,
                    if (archive) R.string.unarchive else R.string.archive,
                ) {
                    onArchive(selected, !archive)
                    selected = emptySet()
                }
                BarAction(R.drawable.ic_delete, R.string.delete) {
                    onDelete(chosen)
                    selected = emptySet()
                }
                // Pin takes the settings button's place, so archive and delete sit where
                // Archived and Trash were and nothing moves when selecting starts.
                if (!archive) {
                    // Pin unless every selected row is pinned already, as other mail and
                    // message apps do with a mixed selection.
                    val pin = chosen.any { !it.flags.pinned }
                    BarAction(R.drawable.ic_pin, if (pin) R.string.pin else R.string.unpin) {
                        onPin(selected, pin)
                        selected = emptySet()
                    }
                }
            }
        } else {
            TopBar(stringResource(if (archive) R.string.archived else R.string.app_name), onBack = onBack) {
                onShowArchive?.let { BarAction(R.drawable.ic_archive, R.string.archived, it) }
                onShowTrash?.let { BarAction(R.drawable.ic_delete, R.string.trash, it) }
                onSettings?.let { BarAction(R.drawable.ic_settings, R.string.settings, it) }
            }
        }
        when {
            conversations == null -> Unit
            conversations.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(if (archive) R.string.archived_empty else R.string.list_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                // Room under the last row so the new-message button does not cover it.
                contentPadding = WindowInsets.navigationBars.add(WindowInsets(top = 4.dp, bottom = 88.dp)).asPaddingValues(),
            ) {
                items(conversations, key = { it.threadId }) { conversation ->
                    val toggle = {
                        val id = conversation.threadId
                        selected = if (id in selected) selected - id else selected + id
                    }
                    ConversationRow(
                        conversation,
                        isSelected = conversation.threadId in selected,
                        showPin = !archive,
                        onClick = { if (selecting) toggle() else onOpen(conversation) },
                        onLongClick = toggle,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
        if (onNewMessage != null && !selecting) {
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
}

@Composable
internal fun BarAction(icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(painterResource(icon), stringResource(label), tint = MaterialTheme.colorScheme.onSurface)
    }
}

private val SelectionSaver = Saver<Set<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toSet() },
)

@Composable
private fun ConversationRow(
    conversation: Conversation,
    isSelected: Boolean,
    showPin: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinned = showPin && conversation.flags.pinned
    val pinnedLabel = if (pinned) stringResource(R.string.pinned) else ""
    val unread = conversation.unreadCount > 0
    val unreadLabel = if (unread) {
        pluralStringResource(R.plurals.unread_count, conversation.unreadCount, conversation.unreadCount)
    } else {
        ""
    }
    // An unsent draft stands in for the last message, as it is what was left to do here.
    val draft = conversation.draft
    val draftLabel = stringResource(R.string.draft_label)
    val snippet = when {
        draft != null -> "$draftLabel $draft"
        conversation.lastFromMe -> stringResource(R.string.you_prefix, conversation.snippet)
        else -> conversation.snippet
    }
    val primary = MaterialTheme.colorScheme.primary
    val shownSnippet = if (draft != null) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) { append(draftLabel) }
            append(" ")
            append(draft)
        }
    } else {
        AnnotatedString(snippet)
    }
    val time = listTime(LocalContext.current, conversation.dateMillis)
    Row(
        modifier
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .fillMaxWidth()
            .card(isSelected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = listOf(conversation.title, pinnedLabel, unreadLabel, snippet, time)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conversation.address, conversation.contact)
        Spacer(Modifier.width(12.dp))
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
                if (pinned) {
                    Icon(
                        painterResource(R.drawable.ic_pin),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
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
                    shownSnippet,
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

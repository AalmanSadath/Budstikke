package dev.aalman.budstikke.ui

import androidx.compose.runtime.collectAsState
import dev.aalman.budstikke.lock.LockSession
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.BlockedEntry
import dev.aalman.budstikke.data.Conversation

/**
 * Spam & blocked: every blocked number, with the conversation it sent when there is one.
 * Tapping a card offers to open that conversation or unblock the number.
 */
@Composable
fun BlockedScreen(
    entries: List<BlockedEntry>?,
    onOpen: (Conversation) -> Unit,
    onUnblock: (BlockedEntry) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.blocked_title), onBack = onBack)
        when {
            entries == null -> Unit
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.blocked_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item {
                    Text(
                        stringResource(R.string.blocked_hint),
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(entries, key = { it.conversation?.threadId?.toString() ?: "address:${it.address}" }) { entry ->
                    BlockedRow(entry, onOpen, onUnblock)
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(entry: BlockedEntry, onOpen: (Conversation) -> Unit, onUnblock: (BlockedEntry) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val conversation = entry.conversation
    val unlocked by LockSession.unlocked.collectAsState()
    val summary = when {
        conversation == null -> stringResource(R.string.blocked_no_messages)
        // Locked text stays hidden here as in the main list.
        conversation.snippetLocked && !unlocked -> stringResource(R.string.locked_message)
        else -> conversation.snippet
    }
    val time = conversation?.let { listTime(context, it.dateMillis) }
    Box {
        Row(
            Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .fillMaxWidth()
                .card()
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(entry.title, summary, time).joinToString(", ")
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(conversation?.address ?: entry.address, entry.contact)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.title,
                        Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (time != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    summary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (conversation != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    onClick = {
                        menu = false
                        onOpen(conversation)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unblock_number)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_block), null) },
                onClick = {
                    menu = false
                    onUnblock(entry)
                },
            )
        }
    }
}

package dev.aalman.budstikke.ui

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
import androidx.compose.foundation.layout.size
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
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.PendingMessage

/** A scheduled message with the contact it goes to, when the number is saved. */
data class ScheduledEntry(val message: PendingMessage, val contact: Contact?) {
    val title: String get() = contact?.name ?: message.address
}

/**
 * Every scheduled message, soonest first. Tapping one offers to open its conversation, send
 * it now or cancel it.
 */
@Composable
fun ScheduledScreen(
    entries: List<ScheduledEntry>?,
    onOpen: (PendingMessage) -> Unit,
    onAction: (PendingMessage, PendingAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.scheduled_title), onBack = onBack)
        when {
            entries == null -> Unit
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.scheduled_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item {
                    Text(
                        stringResource(R.string.scheduled_hint),
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(entries, key = { it.message.id }) { entry -> ScheduledRow(entry, onOpen, onAction) }
            }
        }
    }
}

@Composable
private fun ScheduledRow(
    entry: ScheduledEntry,
    onOpen: (PendingMessage) -> Unit,
    onAction: (PendingMessage, PendingAction) -> Unit,
) {
    val context = LocalContext.current
    val message = entry.message
    var menu by remember { mutableStateOf(false) }
    val time = messageTime(context, message.sendAtMillis)
    val description = stringResource(R.string.scheduled_message, entry.title, time, message.body)
    Box {
        Row(
            Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .fillMaxWidth()
                .card()
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(message.address, entry.contact)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    message.body,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open)) },
                onClick = {
                    menu = false
                    onOpen(message)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.send_now)) },
                onClick = {
                    menu = false
                    onAction(message, PendingAction.SendNow)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cancel)) },
                onClick = {
                    menu = false
                    onAction(message, PendingAction.Cancel)
                },
            )
        }
    }
}

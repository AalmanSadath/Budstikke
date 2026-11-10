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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.aalman.budstikke.data.TrashItem
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * What was deleted in the last 30 days, newest first. Tapping an item offers to restore it
 * or delete it for good; deleting for good asks first, since it cannot be undone.
 */
@Composable
fun TrashScreen(
    items: List<TrashItem>?,
    onRestore: (TrashItem) -> Unit,
    onDeleteForever: (TrashItem) -> Unit,
    onEmpty: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.trash), onBack = onBack) {
            if (!items.isNullOrEmpty()) {
                BarAction(R.drawable.ic_delete, R.string.trash_empty_action) { confirmEmpty = true }
            }
        }
        when {
            items == null -> Unit
            items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item {
                    Text(
                        stringResource(R.string.trash_hint),
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items, key = { it.id }) { item ->
                    TrashRow(item, onRestore = { onRestore(item) }, onDeleteForever = { confirmDelete = item })
                }
            }
        }
    }

    if (confirmEmpty) {
        Confirm(stringResource(R.string.trash_empty_confirm), stringResource(R.string.trash_empty_action), {
            confirmEmpty = false
            onEmpty()
        }) { confirmEmpty = false }
    }
    confirmDelete?.let { item ->
        Confirm(stringResource(R.string.trash_delete_forever_confirm), stringResource(R.string.trash_delete_forever), {
            confirmDelete = null
            onDeleteForever(item)
        }) { confirmDelete = null }
    }
}

@Composable
private fun TrashRow(item: TrashItem, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val summary = if (item.wholeConversation) {
        pluralStringResource(R.plurals.trash_messages, item.messageCount, item.messageCount)
    } else {
        item.lastBody
    }
    val daysLeft = max(
        0L,
        TimeUnit.MILLISECONDS.toDays(item.expiresAtMillis - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1) - 1),
    ).toInt()
    val deleted = stringResource(R.string.trash_deleted, listTime(context, item.deletedAtMillis)) + " · " +
        pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft)
    Box {
        Row(
            Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .fillMaxWidth()
                .card()
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOf(item.title, summary, deleted).joinToString(", ")
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(item.address, item.contact)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(deleted, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.restore)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_restore), null) },
                onClick = {
                    menu = false
                    onRestore()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trash_delete_forever)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                onClick = {
                    menu = false
                    onDeleteForever()
                },
            )
        }
    }
}

@Composable
private fun Confirm(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

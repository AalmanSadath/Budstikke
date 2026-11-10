package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import dev.aalman.budstikke.data.PendingMessage
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import dev.aalman.budstikke.data.Flags
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.Message
import dev.aalman.budstikke.data.MessageState
import dev.aalman.budstikke.sms.Sim

/** One conversation: its messages, newest at the bottom, and the field to reply. */
@Composable
fun ConversationScreen(
    address: String,
    contact: Contact?,
    messages: List<Message>,
    sims: List<Sim>,
    composeText: String,
    onComposeTextChange: (String) -> Unit,
    sim: Sim?,
    onSimChange: (Sim) -> Unit,
    onSend: () -> Unit,
    onRetry: (Message) -> Unit,
    flags: Flags,
    onPinChange: (Boolean) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onDeleteConversation: () -> Unit,
    blocked: Boolean,
    onBlockChange: (Boolean) -> Unit,
    pending: List<PendingMessage>,
    onPendingAction: (PendingMessage, PendingAction) -> Unit,
    onSchedule: () -> Unit,
    onBack: () -> Unit,
) {
    // Long-pressing a bubble starts selecting messages; taps then add or remove them.
    var selected by rememberSaveable(address, stateSaver = MessageSelectionSaver) { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(messages) {
        val present = messages.mapTo(HashSet()) { it.id }
        if (!present.containsAll(selected)) selected = selected intersect present
    }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        if (selecting) {
            TopBar(
                pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                leading = {
                    IconButton(onClick = { selected = emptySet() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            stringResource(R.string.clear_selection),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            ) {
                BarAction(R.drawable.ic_delete, R.string.delete) {
                    onDeleteMessages(selected)
                    selected = emptySet()
                }
            }
        } else {
            TopBar(contact?.name ?: address, onBack = onBack, leading = { Avatar(address, contact, size = 32.dp) }) {
                ConversationMenu(flags, blocked, onPinChange, onArchiveChange, onDeleteConversation, onBlockChange)
            }
        }
        val listState = rememberLazyListState()
        val newest = messages.lastOrNull()
        // The list runs bottom up and keeps its place on the message it was showing, so a
        // new one would land out of sight below. Bring it into view when it is one just sent,
        // or when the newest was already showing; someone reading further up stays put.
        // By the time this runs the new message has pushed the list's place down by one, so
        // "was at the bottom" reads as index 1.
        LaunchedEffect(newest?.id) {
            val wasAtBottom = listState.firstVisibleItemIndex <= 1
            if (newest != null && (newest.isOutgoing || wasAtBottom)) listState.animateScrollToItem(0)
        }
        val focusManager = LocalFocusManager.current
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clearFocusOnTap(focusManager),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Messages still waiting to go out sit below the sent ones, soonest nearest them.
            items(pending.asReversed(), key = { "pending:${it.id}" }) { message ->
                PendingBubble(message, onPendingAction)
            }
            items(messages.asReversed(), key = { it.id }) { message ->
                val toggle = { selected = if (message.id in selected) selected - message.id else selected + message.id }
                Bubble(
                    message,
                    simLabel = sims.labelFor(message),
                    isSelected = message.id in selected,
                    onClick = {
                        when {
                            selecting -> toggle()
                            message.state == MessageState.Failed -> onRetry(message)
                        }
                    },
                    onLongClick = toggle,
                )
            }
        }
        ComposeBar(
            text = composeText,
            onTextChange = onComposeTextChange,
            sims = sims,
            sim = sim,
            onSimChange = onSimChange,
            onSend = onSend,
            modifier = Modifier.navigationBarsPadding(),
            onSchedule = onSchedule,
        )
    }
}

/** What can be done to a message that has not gone out yet. */
enum class PendingAction { SendNow, ChangeTime, Edit, Cancel }

/**
 * A scheduled message: an outgoing bubble, faded, with a clock and its time underneath.
 * Tapping it offers to send it now, change its time, edit it or cancel it.
 */
@Composable
private fun PendingBubble(message: PendingMessage, onAction: (PendingMessage, PendingAction) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val time = messageTime(context, message.sendAtMillis)
    val label = stringResource(R.string.scheduled_for, time)
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label: ${message.body}" }
            .clickable { menu = true },
        horizontalAlignment = Alignment.End,
    ) {
        Column(Modifier.fillMaxWidth(0.8f), horizontalAlignment = Alignment.End) {
            Box {
                Text(
                    message.body,
                    Modifier
                        .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .background(colors.primary.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    fontSize = 16.sp,
                    color = colors.onPrimary,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        PendingAction.SendNow to R.string.send_now,
                        PendingAction.ChangeTime to R.string.change_time,
                        PendingAction.Edit to R.string.edit,
                        PendingAction.Cancel to R.string.cancel,
                    ).forEach { (action, text) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(text)) },
                            onClick = {
                                menu = false
                                onAction(message, action)
                            },
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}

/** Names the SIM a message came in on, only when there is more than one to tell apart. */
private fun List<Sim>.labelFor(message: Message): String? =
    if (size > 1 && !message.isOutgoing) firstOrNull { it.subscriptionId == message.subscriptionId }?.slotLabel else null

@Composable
private fun Bubble(
    message: Message,
    simLabel: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val outgoing = message.isOutgoing
    val failed = message.state == MessageState.Failed
    val time = messageTime(context, message.dateMillis)
    val state = when (message.state) {
        MessageState.Received -> null
        MessageState.Sending -> stringResource(R.string.state_sending)
        MessageState.Sent -> stringResource(R.string.state_sent)
        MessageState.Delivered -> stringResource(R.string.state_delivered)
        MessageState.Failed -> stringResource(R.string.state_failed)
    }
    val description = if (outgoing) {
        stringResource(R.string.message_from_you, time, message.body, state.orEmpty())
    } else {
        stringResource(R.string.message_from_them, time, message.body)
    }
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) primary.copy(alpha = 0.15f) else Color.Transparent)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        val shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (outgoing) 18.dp else 4.dp,
            bottomEnd = if (outgoing) 4.dp else 18.dp,
        )
        // A bubble is never wider than 80% of the screen, and hugs its text below that.
        Column(
            Modifier.fillMaxWidth(0.8f),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            Text(
                message.body,
                Modifier
                    .clip(shape)
                    .background(
                        when {
                            failed -> MaterialTheme.colorScheme.errorContainer
                            outgoing -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                fontSize = 16.sp,
                color = when {
                    failed -> MaterialTheme.colorScheme.onErrorContainer
                    outgoing -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            listOfNotNull(time, simLabel, state).joinToString(" · "),
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pin, archive, block and delete for the open conversation. Pinning is offered only outside the archive. */
@Composable
private fun ConversationMenu(
    flags: Flags,
    blocked: Boolean,
    onPinChange: (Boolean) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onBlockChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(R.drawable.ic_more),
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // A blocked conversation lives only in Spam & blocked, so pins and the archive
            // have nothing to act on until it is unblocked.
            if (!flags.archived && !blocked) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (flags.pinned) R.string.unpin else R.string.pin)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_pin), null) },
                    onClick = {
                        open = false
                        onPinChange(!flags.pinned)
                    },
                )
            }
            if (!blocked) DropdownMenuItem(
                text = { Text(stringResource(if (flags.archived) R.string.unarchive else R.string.archive)) },
                leadingIcon = {
                    Icon(painterResource(if (flags.archived) R.drawable.ic_unarchive else R.drawable.ic_archive), null)
                },
                onClick = {
                    open = false
                    onArchiveChange(!flags.archived)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (blocked) R.string.unblock_number else R.string.block_number)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_block), null) },
                onClick = {
                    open = false
                    onBlockChange(!blocked)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_conversation)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

private val MessageSelectionSaver = Saver<Set<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toSet() },
)

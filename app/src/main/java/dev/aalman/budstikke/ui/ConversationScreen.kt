package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        TopBar(contact?.name ?: address, onBack = onBack, leading = { Avatar(address, contact, size = 32.dp) }) {
            ConversationMenu(flags, onPinChange, onArchiveChange)
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
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                Bubble(message, simLabel = sims.labelFor(message), onRetry = onRetry)
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
        )
    }
}

/** Names the SIM a message came in on, only when there is more than one to tell apart. */
private fun List<Sim>.labelFor(message: Message): String? =
    if (size > 1 && !message.isOutgoing) firstOrNull { it.subscriptionId == message.subscriptionId }?.slotLabel else null

@Composable
private fun Bubble(message: Message, simLabel: String?, onRetry: (Message) -> Unit) {
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
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .then(if (failed) Modifier.clickable { onRetry(message) } else Modifier),
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

/** Pin and archive for the open conversation. Pinning is offered only outside the archive. */
@Composable
private fun ConversationMenu(
    flags: Flags,
    onPinChange: (Boolean) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
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
            if (!flags.archived) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (flags.pinned) R.string.unpin else R.string.pin)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_pin), null) },
                    onClick = {
                        open = false
                        onPinChange(!flags.pinned)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (flags.archived) R.string.unarchive else R.string.archive)) },
                leadingIcon = {
                    Icon(painterResource(if (flags.archived) R.drawable.ic_unarchive else R.drawable.ic_archive), null)
                },
                onClick = {
                    open = false
                    onArchiveChange(!flags.archived)
                },
            )
        }
    }
}

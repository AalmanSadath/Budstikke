package dev.aalman.budstikke.ui

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import dev.aalman.budstikke.data.LockState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.aalman.budstikke.sms.findOneTimeCode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.text.SpanStyle
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
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
import dev.aalman.budstikke.ui.theme.touchTarget
import dev.aalman.budstikke.ui.theme.LocalHighContrast
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.Message
import dev.aalman.budstikke.data.MessageState
import dev.aalman.budstikke.sms.Sim
import dev.aalman.budstikke.sms.REACTION_EMOJI
import dev.aalman.budstikke.sms.foldReactions
import dev.aalman.budstikke.data.MarkState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

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
    /** The slot of the SIM the next message goes out on, for the badge by the name; null for none. */
    simBadge: Int?,
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
    onShowDetails: () -> Unit = {},
    onCopyCode: (String) -> Unit = {},
    locks: LockState = LockState(),
    threadId: Long = -1,
    unlocked: Boolean = true,
    onUnlock: (then: () -> Unit) -> Unit = { it() },
    onLockMessages: (Set<Long>, Boolean) -> Unit = { _, _ -> },
    onLockConversation: (Boolean) -> Unit = {},
    focusMessageId: Long? = null,
    highlight: String? = null,
    marks: MarkState = MarkState(),
    onReact: (Message, String) -> Unit = { _, _ -> },
    onCopy: (List<Message>) -> Unit = {},
    onForward: (Message) -> Unit = {},
    onStar: (Set<Long>, Boolean) -> Unit = { _, _ -> },
) {
    // Reactions that came, or went out, as texts are drawn on the message they quote
    // rather than as messages of their own.
    val folded = remember(messages) { foldReactions(messages) }
    val shown = folded.shown
    val places = remember(shown) { bubblePlaces(shown).let { list -> shown.indices.associate { shown[it].id to list[it] } } }
    val reducedMotion = rememberReducedMotion()
    // Long-pressing a bubble starts selecting messages; taps then add or remove them.
    var selected by rememberSaveable(address, stateSaver = MessageSelectionSaver) { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(shown) {
        val present = shown.mapTo(HashSet()) { it.id }
        if (!present.containsAll(selected)) selected = selected intersect present
    }
    val selecting = selected.isNotEmpty()
    // The emoji bar shows over one held-down message, unless its text is locked away.
    val barFor = selected.singleOrNull()?.takeUnless { it in locks.messages && !unlocked }
    // While it shows, a tap anywhere below the top bar closes it and ends the selection;
    // holding another message down still adds that one.
    val closeBar = remember { { selected = emptySet() } }
    BackHandler(enabled = selecting) { selected = emptySet() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        if (selecting) {
            val chosen = shown.filter { it.id in selected }
            // Copying or forwarding a locked message's text needs the screen lock first.
            val needsUnlock = !unlocked && chosen.any { it.id in locks.messages }
            val withText: (() -> Unit) -> Unit = { then ->
                selected = emptySet()
                if (needsUnlock) onUnlock(then) else then()
            }
            val allStarred = selected.all { it in marks.starred }
            val allLocked = selected.all { it in locks.messages }
            TopBar(
                pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                leading = { BarAction(R.drawable.ic_close, R.string.clear_selection) { selected = emptySet() } },
                items = listOfNotNull(
                    BarItem(R.drawable.ic_copy, R.string.copy) { withText { onCopy(chosen) } },
                    chosen.singleOrNull()?.let { only -> BarItem(R.drawable.ic_forward, R.string.forward) { withText { onForward(only) } } },
                    BarItem(if (allStarred) R.drawable.ic_star else R.drawable.ic_star_outline, if (allStarred) R.string.unstar else R.string.star) {
                        onStar(selected, !allStarred)
                        selected = emptySet()
                    },
                    BarItem(R.drawable.ic_delete, R.string.delete) {
                        // Reaction texts pointing at a deleted message go with it.
                        val ids = selected + selected.flatMap { folded.carriers[it].orEmpty() }
                        selected = emptySet()
                        if (ids.any { it in locks.messages }) onUnlock { onDeleteMessages(ids) } else onDeleteMessages(ids)
                    },
                    BarItem(R.drawable.ic_select_all, R.string.select_all, inMenu = true) {
                        // Locked messages are left out, so they are never deleted along with the rest.
                        selected = shown.filterNot { locks.isLocked(threadId, it.id) }.mapTo(HashSet()) { it.id }
                    },
                    BarItem(if (allLocked) R.drawable.ic_lock_open else R.drawable.ic_lock, if (allLocked) R.string.unlock else R.string.lock, inMenu = true) {
                        val ids = selected
                        selected = emptySet()
                        // Taking a lock off needs the screen lock; putting one on does not.
                        if (allLocked) onUnlock { onLockMessages(ids, false) } else onLockMessages(ids, true)
                    },
                ),
            )
        } else {
            TopBar(
                contact?.name ?: address,
                onBack = onBack,
                leading = { Avatar(address, contact, size = 32.dp) },
                onTitleClick = onShowDetails,
                titleTrailing = simBadge?.let { slot -> { SimBadge(slot, 18.dp, Modifier.padding(end = 8.dp)) } },
                titleTrailingWidth = if (simBadge != null) 18.dp * 1.5f + 8.dp else 0.dp,
            ) {
                ConversationMenu(
                    flags, blocked, threadId in locks.threads, onShowDetails, onPinChange, onArchiveChange,
                    onDeleteConversation, onBlockChange, onLockConversation,
                )
            }
        }
        val listState = rememberLazyListState()
        val newest = shown.lastOrNull()
        // Opened at a message, from search: the list goes there once messages load, and that
        // message is tinted for a moment so the eye finds it. Until the person drags the list,
        // sends or schedules, the list stays anchored there: messages loading, a pending one
        // loading after them, or one arriving must not pull it back to the bottom.
        var anchored by remember(focusMessageId) { mutableStateOf(focusMessageId != null) }
        var focusDone by remember(focusMessageId) { mutableStateOf(focusMessageId == null) }
        var flashId by remember(focusMessageId) { mutableStateOf<Long?>(null) }
        LaunchedEffect(focusMessageId, shown.isNotEmpty()) {
            if (focusDone || shown.isEmpty()) return@LaunchedEffect
            focusDone = true
            val at = shown.indexOfFirst { it.id == focusMessageId }
            if (at < 0) {
                anchored = false
                return@LaunchedEffect
            }
            // Bottom up, with pending messages below the rest.
            listState.scrollToItem(pending.size + shown.lastIndex - at)
            // Lift it from the bottom edge to about a third of the way up.
            listState.scrollBy(-listState.layoutInfo.viewportSize.height / 3f)
            flashId = focusMessageId
            delay(FOCUS_FLASH_MILLIS)
            flashId = null
        }
        // Only a drag by the person counts; the scrolls above do not.
        LaunchedEffect(listState) {
            listState.interactionSource.interactions.collect {
                if (it is DragInteraction.Start) anchored = false
            }
        }
        // The list runs bottom up and keeps its place on the message it was showing, so a
        // new one would land out of sight below. Bring it into view when it is one just sent,
        // or when the newest was already showing; someone reading further up stays put.
        // By the time this runs the new message has pushed the list's place down by one, so
        // "was at the bottom" reads as index 1.
        LaunchedEffect(newest?.id) {
            if (anchored) return@LaunchedEffect
            val wasAtBottom = listState.firstVisibleItemIndex <= 1
            if (newest != null && (newest.isOutgoing || wasAtBottom)) listState.scrollToBottom(reducedMotion)
        }
        // A message just held back by the send delay, or just scheduled, is added below the
        // rest the same way; bring it into view.
        val newestPending = pending.maxOfOrNull { it.id }
        LaunchedEffect(newestPending) {
            if (newestPending != null && !anchored) listState.scrollToBottom(reducedMotion)
        }
        val focusManager = LocalFocusManager.current
        // A locked conversation shows nothing of itself until the screen lock is confirmed,
        // not even a draft in the message field.
        if (threadId in locks.threads && !unlocked) {
            LockedPane(onUnlock = { onUnlock {} }, modifier = Modifier.weight(1f))
            return@Column
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clearFocusOnTap(focusManager)
                .onTapAnywhere(barFor != null, closeBar),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Messages still waiting to go out sit below the sent ones, soonest nearest them.
            items(pending.asReversed(), key = { "pending:${it.id}" }) { message ->
                Box(Modifier.motion(this, reducedMotion).padding(top = GROUP_SPACING)) { PendingBubble(message, onPendingAction) }
            }
            items(shown.asReversed(), key = { it.id }) { message ->
                val place = places[message.id] ?: BubblePlace(timeLabel = false, joinedAbove = false, joinedBelow = false)
                Column(Modifier.motion(this, reducedMotion).padding(top = if (place.joinedAbove) JOINED_SPACING else GROUP_SPACING)) {
                    if (place.timeLabel) TimeLabel(message.dateMillis)
                    val toggle = { selected = if (message.id in selected) selected - message.id else selected + message.id }
                    Bubble(
                        message,
                        simLabel = sims.labelFor(message),
                        isSelected = message.id in selected,
                        flash = message.id == flashId,
                        highlight = highlight,
                        locked = message.id in locks.messages,
                        hidden = message.id in locks.messages && !unlocked,
                        starred = message.id in marks.starred,
                        mine = marks.reactions[message.id],
                        theirs = folded.theirs[message.id],
                        // One message held down offers the emoji; a locked one only once it shows.
                        reactionBar = barFor == message.id,
                        onReact = { emoji ->
                            selected = emptySet()
                            onReact(message, emoji)
                        },
                        // While selecting, a tap on the code selects like a tap anywhere else.
                        onCopyCode = if (selecting) null else onCopyCode,
                        onClick = {
                            when {
                                barFor != null -> selected = emptySet()
                                selecting -> toggle()
                                message.id in locks.messages && !unlocked -> onUnlock {}
                                message.state == MessageState.Failed -> onRetry(message)
                            }
                        },
                        onLongClick = toggle,
                        place = place,
                        sender = contact?.name ?: address,
                    )
                }
            }
        }
        ComposeBar(
            text = composeText,
            onTextChange = onComposeTextChange,
            sims = sims,
            sim = sim,
            onSimChange = onSimChange,
            onSend = {
                anchored = false
                onSend()
            },
            modifier = Modifier.navigationBarsPadding().onTapAnywhere(barFor != null, closeBar),
            onSchedule = {
                anchored = false
                onSchedule()
            },
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
    if (message.delayed) {
        DelayedBubble(message) { onAction(message, PendingAction.Edit) }
        return
    }
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

/**
 * A message held back by the send delay: a faded outgoing bubble with a countdown to when
 * it goes and a Cancel button, which puts the text back in the field.
 */
@Composable
private fun DelayedBubble(message: PendingMessage, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis(), message.sendAtMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(200)
        }
    }
    val label = stringResource(R.string.sending_in, secondsLeft(message.sendAtMillis, now))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // Read as one, the countdown with the text; Cancel stays its own button.
        Column(
            Modifier.fillMaxWidth(0.8f).clearAndSetSemantics { contentDescription = "$label: ${message.body}" },
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                message.body,
                Modifier
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(colors.primary.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                fontSize = 16.sp,
                color = colors.onPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.padding(start = 6.dp), fontSize = 11.sp, color = colors.onSurfaceVariant)
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** Whole seconds until [sendAtMillis], rounded up so the count reaches 1 before it goes, never below 0. */
internal fun secondsLeft(sendAtMillis: Long, nowMillis: Long): Int =
    ((sendAtMillis - nowMillis + 999) / 1000).coerceAtLeast(0).toInt()

/** Names the SIM a message came in on, only when there is more than one to tell apart. */
private fun List<Sim>.labelFor(message: Message): String? =
    if (size > 1 && !message.isOutgoing) firstOrNull { it.subscriptionId == message.subscriptionId }?.slotLabel else null

@Composable
private fun Bubble(
    message: Message,
    simLabel: String?,
    isSelected: Boolean,
    flash: Boolean,
    highlight: String?,
    locked: Boolean,
    hidden: Boolean,
    starred: Boolean,
    mine: String?,
    theirs: String?,
    reactionBar: Boolean,
    onReact: (String) -> Unit,
    onCopyCode: ((String) -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    place: BubblePlace,
    sender: String,
) {
    val context = LocalContext.current
    val outgoing = message.isOutgoing
    val failed = message.state == MessageState.Failed
    val highContrast = LocalHighContrast.current
    val time = messageTime(context, message.dateMillis)
    val state = when (message.state) {
        MessageState.Received -> null
        MessageState.Sending -> stringResource(R.string.state_sending)
        MessageState.Sent -> stringResource(R.string.state_sent)
        MessageState.Delivered -> stringResource(R.string.state_delivered)
        MessageState.Failed -> stringResource(R.string.state_failed)
    }
    // A locked message keeps its text out of the bubble and out of TalkBack until unlocked.
    val body = if (hidden) stringResource(R.string.locked_message) else message.body
    val reactions = listOfNotNull(
        mine?.let { stringResource(R.string.reaction_yours, it) },
        theirs?.let { stringResource(R.string.reaction_theirs, it) },
    )
    val description = (
        if (outgoing) {
            stringResource(R.string.message_from_you, time, body, state.orEmpty())
        } else {
            stringResource(R.string.message_from_them, sender, time, body)
        }
        ) + listOfNotNull(
        reactions.takeIf { it.isNotEmpty() }?.let { "${stringResource(R.string.reactions_label)}: ${it.joinToString(", ")}" },
        if (starred) stringResource(R.string.starred) else null,
        if (locked) stringResource(R.string.locked) else null,
    ).joinToString("") { ". $it" }
    val align = if (outgoing) Alignment.End else Alignment.Start
    val primary = MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(
        when {
            isSelected -> primary.copy(alpha = 0.15f)
            flash -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
        animationSpec = if (rememberReducedMotion()) snap() else tween(FLASH_FADE_MILLIS),
        label = "bubble tint",
    )
    val marked = MaterialTheme.colorScheme.let { colors ->
        SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
    }
    val textColour = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        outgoing -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    // A one-time code reads as a link: underlined, bold, in the accent on the other side's
    // bubbles and in the text colour on this side's, which are already the accent.
    val code = remember(message.body, hidden) { if (hidden) null else findOneTimeCode(message.body) }
    val linkColour = if (outgoing || failed) textColour else MaterialTheme.colorScheme.primary
    val shown = (if (hidden) AnnotatedString(body) else markMatches(message.body, highlight, marked)).let { base ->
        if (code == null || onCopyCode == null) return@let base
        buildAnnotatedString {
            append(base)
            addLink(
                LinkAnnotation.Clickable(
                    tag = "one-time-code",
                    styles = TextLinkStyles(
                        SpanStyle(color = linkColour, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                    ),
                ) { onCopyCode(code.code) },
                code.range.first,
                code.range.last + 1,
            )
        }
    }
    val copyCodeLabel = code?.let { stringResource(R.string.copy_code, it.code) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (reactionBar) ReactionBar(mine, alignEnd = outgoing, onReact)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tint)
                .clearAndSetSemantics {
                    contentDescription = description
                    selected = isSelected
                    // The text's own link is hidden by clearing, so TalkBack gets it as an action.
                    if (code != null && onCopyCode != null && copyCodeLabel != null) {
                        customActions = listOf(CustomAccessibilityAction(copyCodeLabel) { onCopyCode(code.code); true })
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(2.dp),
            horizontalAlignment = align,
        ) {
            // Bubbles of one group share their inner side: the corners where they meet are tight,
            // and the last keeps the small corner that points at who sent it.
            val joinedTop = if (place.joinedAbove) 4.dp else 18.dp
            val shape = RoundedCornerShape(
                topStart = if (outgoing) 18.dp else joinedTop,
                topEnd = if (outgoing) joinedTop else 18.dp,
                bottomStart = if (outgoing) 18.dp else 4.dp,
                bottomEnd = if (outgoing) 4.dp else 18.dp,
            )
            // A bubble is never wider than 80% of the screen, and hugs its text below that.
            Column(
                Modifier.fillMaxWidth(0.8f),
                horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
            ) {
                Text(
                    shown,
                    Modifier
                        // In high contrast a received bubble is outlined: its fill is close to the page.
                        .then(if (highContrast && !outgoing) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
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
                    color = textColour,
                    fontStyle = if (hidden) FontStyle.Italic else FontStyle.Normal,
                )
                if (mine != null || theirs != null) ReactionChip(listOfNotNull(theirs, mine).distinct(), mine != null)
            }
            // Inside a group only the last message names the time and state, unless one needs
            // its own: it failed, or carries a star or a lock.
            if (!place.joinedBelow || failed || starred || locked) Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (starred) {
                    Icon(
                        painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                }
                // Locked messages carry a small lock by their time, shown or hidden.
                if (locked) {
                    Icon(
                        painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    listOfNotNull(time, simLabel, state).joinToString(" · "),
                    fontSize = 11.sp,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The emoji offered over a message held down, floating above it over the rest of the list,
 * or below it when it sits too near the top. The one this phone already put on it is
 * ringed, and tapping it again takes it off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionBar(mine: String?, alignEnd: Boolean, onReact: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    val position = remember(alignEnd, gap) { FloatAbove(alignEnd, gap) }
    // Emoji grow with the text size as the bar's icons do, up to half again their size.
    val emojiSize = 24.dp * density.fontScale.coerceIn(1f, 1.5f)
    val cell = maxOf(touchTarget(44.dp), emojiSize + 12.dp)
    val screen = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val perRow = if (cell * REACTION_EMOJI.size + 32.dp <= screen) REACTION_EMOJI.size else REACTION_EMOJI.size / 2
    // Not focusable, so a tap on the list still reaches the list and Back still clears
    // the selection, which closes this.
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHighest, shadowElevation = 6.dp) {
            // One row when it fits, two of four when larger text makes it too wide.
            FlowRow(Modifier.padding(4.dp), maxItemsInEachRow = perRow) {
                REACTION_EMOJI.forEach { emoji ->
                    val chosen = emoji == mine
                    val label = stringResource(if (chosen) R.string.remove_reaction else R.string.react_with, emoji)
                    Box(
                        Modifier
                            .size(cell)
                            .clip(CircleShape)
                            .background(if (chosen) colors.primary.copy(alpha = 0.25f) else Color.Transparent)
                            .clearAndSetSemantics {
                                contentDescription = label
                                selected = chosen
                            }
                            .clickable { onReact(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, fontSize = with(density) { emojiSize.toSp() })
                    }
                }
            }
        }
    }
}

/**
 * Places a popup just above its anchor, lined up with the anchor's start or end edge and
 * kept inside the window; below the anchor when there is no room above.
 */
private class FloatAbove(private val alignEnd: Boolean, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val end = if (layoutDirection == LayoutDirection.Ltr) alignEnd else !alignEnd
        val x = if (end) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val above = anchorBounds.top - popupContentSize.height - gap
        val y = if (above >= 0) above else anchorBounds.bottom + gap
        return IntOffset(x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)), y)
    }
}

/**
 * The reactions on a message, on the bubble's lower edge. Ringed in the background colour
 * so it reads as sitting over the bubble; tinted when one of them is this phone's.
 */
@Composable
private fun ReactionChip(emoji: List<String>, includesMine: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Text(
        emoji.joinToString(" "),
        Modifier
            .padding(horizontal = 10.dp)
            // Overlap the bubble above by a few dp, taking that much less room below.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = REACTION_OVERLAP.roundToPx()
                layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) { placeable.place(0, -overlap) }
            }
            .border(2.dp, colors.background, shape)
            .clip(shape)
            .background(if (includesMine) colors.secondaryContainer else colors.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 14.sp,
    )
}

private val REACTION_OVERLAP = 6.dp

/** Space above a message that starts a group, and above one that joins the message before. */
private val GROUP_SPACING = 10.dp
private val JOINED_SPACING = 1.dp

/**
 * A message arriving, going out or leaving fades in or out while the rest slide to make
 * room, unless motion is turned down; then it appears and goes at once.
 */
private fun Modifier.motion(item: LazyItemScope, reduced: Boolean): Modifier =
    if (reduced) {
        this
    } else {
        with(item) {
            animateItem(
                fadeInSpec = tween(MOTION_MILLIS),
                placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                fadeOutSpec = tween(MOTION_MILLIS / 2),
            )
        }
    }

private const val MOTION_MILLIS = 250

/** Brings the newest message into view: scrolling there, or jumping when motion is turned down. */
private suspend fun LazyListState.scrollToBottom(reduced: Boolean) {
    if (reduced) scrollToItem(0) else animateScrollToItem(0)
}

/** Above a group that starts the conversation or follows a long pause: when it began. */
@Composable
private fun TimeLabel(millis: Long) {
    val time = messageTime(LocalContext.current, millis)
    Text(
        time,
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Pin, archive, block and delete for the open conversation. Pinning is offered only outside the archive. */
@Composable
private fun ConversationMenu(
    flags: Flags,
    blocked: Boolean,
    threadLocked: Boolean,
    onShowDetails: () -> Unit,
    onPinChange: (Boolean) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onBlockChange: (Boolean) -> Unit,
    onLockChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarAction(R.drawable.ic_more, R.string.more_options) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.details)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_person), null) },
                onClick = {
                    open = false
                    onShowDetails()
                },
            )
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
                text = { Text(stringResource(if (threadLocked) R.string.unlock_conversation else R.string.lock_conversation)) },
                leadingIcon = { Icon(painterResource(if (threadLocked) R.drawable.ic_lock_open else R.drawable.ic_lock), null) },
                onClick = {
                    open = false
                    onLockChange(!threadLocked)
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

/** How long a message opened from search stays tinted, and how long the tint takes to fade. */
private const val FOCUS_FLASH_MILLIS = 2_000L
private const val FLASH_FADE_MILLIS = 600

/** In place of a locked conversation: what it is, and a button to confirm the screen lock. */
@Composable
private fun LockedPane(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.conversation_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUnlock) { Text(stringResource(R.string.unlock)) }
    }
}

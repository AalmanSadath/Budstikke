package dev.aalman.budstikke

import android.os.PersistableBundle
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.ClipData
import dev.aalman.budstikke.ui.DetailsScreen
import android.provider.ContactsContract
import android.net.Uri
import android.graphics.Rect
import android.content.ActivityNotFoundException
import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import dev.aalman.budstikke.data.Conversation
import dev.aalman.budstikke.data.ConversationFlags
import dev.aalman.budstikke.data.Flags
import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aalman.budstikke.data.Trash
import dev.aalman.budstikke.ui.TrashScreen
import dev.aalman.budstikke.ui.UndoQueue
import kotlinx.coroutines.CoroutineScope
import dev.aalman.budstikke.data.BlockList
import dev.aalman.budstikke.data.Folder
import dev.aalman.budstikke.ui.BlockedScreen
import androidx.compose.runtime.rememberUpdatedState
import dev.aalman.budstikke.data.Drafts
import kotlinx.coroutines.delay
import dev.aalman.budstikke.data.Outgoing
import dev.aalman.budstikke.data.PendingMessage
import dev.aalman.budstikke.sms.OutgoingQueue
import dev.aalman.budstikke.ui.PendingAction
import dev.aalman.budstikke.ui.SchedulePicker
import dev.aalman.budstikke.ui.ScheduledEntry
import dev.aalman.budstikke.ui.ScheduledScreen
import dev.aalman.budstikke.ui.messageTime
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.ContactRepository
import dev.aalman.budstikke.data.ConversationRepository
import dev.aalman.budstikke.data.MessageRepository
import dev.aalman.budstikke.data.SearchResult
import dev.aalman.budstikke.notify.ActiveConversation
import dev.aalman.budstikke.notify.MessageNotifier
import dev.aalman.budstikke.settings.SettingsRepository
import dev.aalman.budstikke.sms.Sim
import dev.aalman.budstikke.sms.SmsSender
import dev.aalman.budstikke.sms.activeSims
import dev.aalman.budstikke.sms.preferredSim
import dev.aalman.budstikke.ui.ConversationListScreen
import dev.aalman.budstikke.ui.ConversationScreen
import dev.aalman.budstikke.ui.RoleScreen
import dev.aalman.budstikke.ui.SearchScreen
import dev.aalman.budstikke.ui.SettingsScreen
import dev.aalman.budstikke.ui.theme.BudstikkeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A conversation open on screen: its thread in the message store and who it is with. */
private data class OpenThread(
    val threadId: Long,
    val address: String,
    /** A message to scroll to on opening, such as the one a search found. */
    val focusMessageId: Long? = null,
    /** Text to mark in every message, the search that led here. */
    val highlight: String? = null,
)

class MainActivity : ComponentActivity() {

    private val roleManager by lazy { getSystemService(RoleManager::class.java) }
    private var isDefaultSmsApp by mutableStateOf(false)
    private var openThread by mutableStateOf<OpenThread?>(null)
    private var showSettings by mutableStateOf(false)
    private var showArchive by mutableStateOf(false)
    private var showTrash by mutableStateOf(false)
    private var showBlocked by mutableStateOf(false)
    private var showScheduled by mutableStateOf(false)
    private var showSearch by mutableStateOf(false)
    // Kept here rather than in the search screen, which leaves composition while a
    // conversation opened from it is showing; back from there finds the same search.
    private var searchQuery by mutableStateOf("")
    private var sims by mutableStateOf<List<Sim>>(emptyList())

    private val requestRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshRole()
        }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            sims = activeSims(this)
            if (granted[Manifest.permission.READ_CONTACTS] == true) ContactRepository.get(this).invalidate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshRole()
        openThread = savedInstanceState?.let { state ->
            state.getString(STATE_ADDRESS)?.let { OpenThread(state.getLong(STATE_THREAD_ID), it) }
        } ?: threadFrom(intent)
        showSettings = savedInstanceState?.getBoolean(STATE_SETTINGS) ?: false
        showArchive = savedInstanceState?.getBoolean(STATE_ARCHIVE) ?: false
        showTrash = savedInstanceState?.getBoolean(STATE_TRASH) ?: false
        showBlocked = savedInstanceState?.getBoolean(STATE_BLOCKED) ?: false
        showScheduled = savedInstanceState?.getBoolean(STATE_SCHEDULED) ?: false
        showSearch = savedInstanceState?.getBoolean(STATE_SEARCH) ?: false
        searchQuery = savedInstanceState?.getString(STATE_SEARCH_QUERY).orEmpty()

        val conversations = ConversationRepository(this)
        val messages = MessageRepository(this)
        val sender = SmsSender(this)
        val roleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_SMS)

        setContent {
            BudstikkeTheme {
                val thread = openThread
                // One undo queue above every screen, so a deletion made in a conversation
                // can still be undone from the list it returns to.
                val scope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                val undoLabel = stringResource(R.string.undo)
                val undo = remember(undoLabel) { UndoQueue(scope, snackbar, undoLabel) }
                Box(Modifier.fillMaxSize()) {
                when {
                    !isDefaultSmsApp -> RoleScreen(roleAvailable) {
                        requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    }
                    thread != null -> ConversationRoute(thread, messages, sender, sims, undo) { openThread = null }
                    showSearch -> {
                        val closeSearch = {
                            showSearch = false
                            searchQuery = ""
                        }
                        BackHandler(onBack = closeSearch)
                        SearchRoute(
                            conversations,
                            searchQuery,
                            onQueryChange = { searchQuery = it },
                            onOpen = { result ->
                                val conversation = result.conversation
                                openThread = OpenThread(
                                    conversation.threadId,
                                    conversation.address,
                                    focusMessageId = result.message?.id,
                                    highlight = searchQuery.trim().takeIf { result.message != null },
                                )
                            },
                            onBack = closeSearch,
                        )
                    }
                    showBlocked -> {
                        BackHandler { showBlocked = false }
                        BlockedRoute(
                            conversations,
                            undo,
                            onOpen = { openThread = OpenThread(it.threadId, it.address) },
                            onBack = { showBlocked = false },
                        )
                    }
                    showScheduled -> {
                        BackHandler { showScheduled = false }
                        ScheduledRoute(
                            undo,
                            onOpen = { openThread = OpenThread(it.threadId, it.address) },
                            onBack = { showScheduled = false },
                        )
                    }
                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            sims = sims,
                            onShowScheduled = { showScheduled = true },
                            onShowBlocked = { showBlocked = true },
                            onBack = { showSettings = false },
                        )
                    }
                    showTrash -> {
                        BackHandler { showTrash = false }
                        TrashRoute(undo) { showTrash = false }
                    }
                    showArchive -> {
                        BackHandler { showArchive = false }
                        ConversationListRoute(
                            conversations,
                            undo,
                            archive = true,
                            onOpen = { openThread = OpenThread(it.threadId, it.address) },
                            onBack = { showArchive = false },
                        )
                    }
                    else -> ConversationListRoute(
                        conversations,
                        undo,
                        archive = false,
                        onOpen = { openThread = OpenThread(it.threadId, it.address) },
                        onNewMessage = { startActivity(Intent(this@MainActivity, ComposeActivity::class.java)) },
                        onShowArchive = { showArchive = true },
                        onShowTrash = { showTrash = true },
                        onSettings = { showSettings = true },
                        onSearch = { showSearch = true },
                    )
                }
                    SnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            // Clear of the message field when a conversation is open.
                            .padding(bottom = if (thread != null) 64.dp else 0.dp),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        threadFrom(intent)?.let { openThread = it }
    }

    override fun onResume() {
        super.onResume()
        // The role can be taken away in system settings, and SIMs can change, while the
        // app is in the background.
        refreshRole()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SETTINGS, showSettings)
        outState.putBoolean(STATE_ARCHIVE, showArchive)
        outState.putBoolean(STATE_TRASH, showTrash)
        outState.putBoolean(STATE_BLOCKED, showBlocked)
        outState.putBoolean(STATE_SCHEDULED, showScheduled)
        outState.putBoolean(STATE_SEARCH, showSearch)
        outState.putString(STATE_SEARCH_QUERY, searchQuery)
        openThread?.let {
            outState.putLong(STATE_THREAD_ID, it.threadId)
            outState.putString(STATE_ADDRESS, it.address)
        }
    }

    private fun refreshRole() {
        isDefaultSmsApp = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        if (!isDefaultSmsApp) return
        sims = activeSims(this)
        // Phone state lists the SIMs; without it messages go out on the system's default
        // SIM. Contacts give names and photos; without them numbers show. Notifications need
        // asking for from Android 13. Both are asked for once per
        // launch, and everything else works if they are refused.
        val missing = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !askedForPermissions) {
            askedForPermissions = true
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private var askedForPermissions = false

    private fun threadFrom(intent: Intent?): OpenThread? {
        val threadId = intent?.getLongExtra(EXTRA_THREAD_ID, -1L) ?: -1L
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        return if (threadId >= 0 && address != null) OpenThread(threadId, address) else null
    }

    companion object {
        private const val EXTRA_THREAD_ID = "dev.aalman.budstikke.THREAD_ID"
        private const val EXTRA_ADDRESS = "dev.aalman.budstikke.ADDRESS"
        private const val STATE_THREAD_ID = "thread_id"
        private const val STATE_ADDRESS = "address"
        private const val STATE_SETTINGS = "settings"
        private const val STATE_ARCHIVE = "archive"
        private const val STATE_TRASH = "trash"
        private const val STATE_BLOCKED = "blocked"
        private const val STATE_SCHEDULED = "scheduled"
        private const val STATE_SEARCH = "search"
        private const val STATE_SEARCH_QUERY = "search_query"

        /** Opens one conversation, reusing the activity if it is already running. */
        fun openConversation(context: Context, threadId: Long, address: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_THREAD_ID, threadId)
                .putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/** How long typing must pause before the draft is saved. */
private const val DRAFT_SAVE_DELAY_MILLIS = 500L

@Composable
private fun ConversationRoute(
    thread: OpenThread,
    repository: MessageRepository,
    sender: SmsSender,
    sims: List<Sim>,
    undo: UndoQueue,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(thread.threadId) { repository.messages(thread.threadId) }
    val messages by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var text by rememberSaveable(thread.threadId) { mutableStateOf("") }
    // Unsent text is kept as a draft: filled in when the conversation opens, saved shortly
    // after typing stops, and saved at once when the screen or the app is left.
    val drafts = remember { Drafts.get(context) }
    var draftLoaded by rememberSaveable(thread.threadId) { mutableStateOf(false) }
    LaunchedEffect(thread.threadId) {
        if (!draftLoaded) {
            val saved = withContext(Dispatchers.IO) { drafts.get(thread.threadId) }
            if (text.isEmpty() && saved != null) text = saved
            draftLoaded = true
        }
    }
    LaunchedEffect(thread.threadId, text, draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect
        delay(DRAFT_SAVE_DELAY_MILLIS)
        drafts.put(thread.threadId, text)
    }
    val latestText by rememberUpdatedState(text)
    val latestLoaded by rememberUpdatedState(draftLoaded)
    val saveDraft = { if (latestLoaded) drafts.put(thread.threadId, latestText) }
    var chosenSubscription by rememberSaveable(thread.threadId) { mutableStateOf<Int?>(null) }
    val settings by SettingsRepository.get(context).settings.collectAsState()
    val sim = sims.firstOrNull { it.subscriptionId == chosenSubscription }
        ?: preferredSim(sims, settings.defaultSubscriptionId)
    val contacts = remember { ContactRepository.get(context) }
    val flagStore = remember { ConversationFlags.get(context) }
    val trash = remember { Trash.get(context) }
    val resources = LocalResources.current
    val blockList = remember { BlockList.get(context) }
    val blocked by produceState(false, thread.address) {
        blockList.changes().collect { value = withContext(Dispatchers.IO) { blockList.isBlocked(thread.address) } }
    }
    val outgoing = remember { Outgoing.get(context) }
    val pendingFlow = remember(thread.threadId) { outgoing.forThread(thread.threadId) }
    val pending by pendingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Null when closed; holds the message whose time is being changed, or nothing for a new one.
    var picker by remember { mutableStateOf<SchedulePick?>(null) }
    val flags by produceState(Flags(), thread.threadId) {
        flagStore.changes().collect { value = withContext(Dispatchers.IO) { flagStore.of(thread.threadId) } }
    }
    val contact by produceState<Contact?>(null, thread.address) {
        value = withContext(Dispatchers.IO) { contacts.lookup(thread.address) }
        contacts.changes().collect {
            value = withContext(Dispatchers.IO) { contacts.lookup(thread.address) }
        }
    }

    // While the conversation is on screen, what arrives in it is seen: no notification,
    // and it is marked read. Leaving the screen or the app ends that.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(thread.threadId, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ActiveConversation.threadId = thread.threadId
                Lifecycle.Event.ON_PAUSE -> {
                    ActiveConversation.threadId = null
                    saveDraft()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            saveDraft()
            if (ActiveConversation.threadId == thread.threadId) ActiveConversation.threadId = null
        }
    }
    LaunchedEffect(thread.threadId, messages.size) {
        repository.markRead(thread.threadId)
        MessageNotifier(context).cancel(thread.threadId)
    }

    val onArchiveChange: (Boolean) -> Unit = { archived ->
        scope.launch(Dispatchers.IO) { flagStore.setArchived(listOf(thread.threadId), archived) }
        // An archived conversation leaves the list it was opened from, so go back to it.
        if (archived) onBack()
    }
    val onBlockChange: (Boolean) -> Unit = { block ->
        if (block) {
            val title = contact?.name ?: thread.address
            blockConversations(scope, undo, resources, context, listOf(Triple(thread.threadId, thread.address, title)))
            // The conversation moves to Spam & blocked, out of the list it was opened from.
            onBack()
        } else {
            scope.launch(Dispatchers.IO) { blockList.unblock(thread.address) }
        }
    }
    // The details screen stands in for the conversation, so back from it returns here.
    var showDetails by rememberSaveable(thread.threadId) { mutableStateOf(false) }
    if (showDetails) {
        BackHandler { showDetails = false }
        DetailsScreen(
            address = thread.address,
            contact = contact,
            flags = flags,
            blocked = blocked,
            onCall = { openExternal(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", thread.address, null))) },
            onContact = { openContact(context, contact, thread.address) },
            onBlockChange = onBlockChange,
            onArchiveChange = onArchiveChange,
            onBack = { showDetails = false },
        )
        return
    }

    ConversationScreen(
        address = thread.address,
        onShowDetails = { showDetails = true },
        onCopyCode = { code ->
            copyCode(context, code)
            // Android 13 and later confirm a copy themselves; earlier ones do not.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                scope.launch {
                    undo.snackbar.currentSnackbarData?.dismiss()
                    undo.snackbar.showSnackbar(resources.getString(R.string.code_copied))
                }
            }
        },
        focusMessageId = thread.focusMessageId,
        highlight = thread.highlight,
        contact = contact,
        messages = messages,
        sims = sims,
        composeText = text,
        onComposeTextChange = { text = it },
        sim = sim,
        onSimChange = { chosenSubscription = it.subscriptionId },
        onSend = {
            val body = text.trim()
            if (body.isNotEmpty()) {
                text = ""
                val subscription = sim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                scope.launch(Dispatchers.IO) {
                    OutgoingQueue.sendOrHold(context, thread.threadId, thread.address, body, subscription)
                }
            }
        },
        onRetry = { message -> scope.launch(Dispatchers.IO) { sender.retry(message.id) } },
        pending = pending,
        onSchedule = { if (text.isNotBlank()) picker = SchedulePick(null) },
        onPendingAction = { message, action ->
            when (action) {
                PendingAction.ChangeTime -> picker = SchedulePick(message)
                PendingAction.Edit -> scope.launch {
                    val claimed = withContext(Dispatchers.IO) { outgoing.claim(message.id) } ?: return@launch
                    // Back into the field to change; scheduling again is a long press away.
                    text = if (text.isBlank()) claimed.body else "${text.trimEnd()} ${claimed.body}"
                }
                else -> pendingAction(scope, context, undo, resources, message, action)
            }
        },
        flags = flags,
        onPinChange = { pinned ->
            scope.launch(Dispatchers.IO) { flagStore.setPinned(listOf(thread.threadId), pinned) }
        },
        onArchiveChange = onArchiveChange,
        onDeleteMessages = { ids ->
            scope.launch {
                val item = withContext(Dispatchers.IO) { trash.deleteMessages(thread.address, ids) }
                    ?: return@launch
                undo.push(resources.getQuantityString(R.plurals.deleted_messages, ids.size, ids.size)) {
                    trash.restore(listOf(item))
                }
            }
        },
        blocked = blocked,
        onBlockChange = onBlockChange,
        onDeleteConversation = {
            deleteConversations(scope, trash, undo, resources, mapOf(thread.threadId to thread.address), context)
            onBack()
        },
        onBack = onBack,
    )
    picker?.let { pick ->
        SchedulePicker(
            initialMillis = pick.message?.sendAtMillis,
            onDismiss = { picker = null },
            onPick = { millis ->
                picker = null
                val changing = pick.message
                if (changing != null) {
                    scope.launch(Dispatchers.IO) { outgoing.reschedule(changing.id, millis) }
                } else {
                    val body = text.trim()
                    if (body.isEmpty()) return@SchedulePicker
                    text = ""
                    val subscription = sim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            outgoing.add(thread.threadId, thread.address, body, subscription, millis, delayed = false)
                        }
                        undo.snackbar.currentSnackbarData?.dismiss()
                        undo.snackbar.showSnackbar(resources.getString(R.string.scheduled_done, messageTime(context, millis)))
                    }
                }
            },
        )
    }
}

/**
 * The main list or the archive, with pinning, archiving and deleting. Archiving,
 * unarchiving and deleting offer undo; undoing an archive puts pins back too.
 */
@Composable
private fun ConversationListRoute(
    repository: ConversationRepository,
    undo: UndoQueue,
    archive: Boolean,
    onOpen: (Conversation) -> Unit,
    onBack: (() -> Unit)? = null,
    onNewMessage: (() -> Unit)? = null,
    onShowArchive: (() -> Unit)? = null,
    onShowTrash: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val flagStore = remember { ConversationFlags.get(context) }
    val trash = remember { Trash.get(context) }
    val flow = remember(archive) { repository.conversations(if (archive) Folder.Archive else Folder.Main) }
    val list by flow.collectAsStateWithLifecycle(initialValue = null)
    ConversationListScreen(
        conversations = list,
        archive = archive,
        onOpen = onOpen,
        onPin = { ids, pinned -> scope.launch(Dispatchers.IO) { flagStore.setPinned(ids, pinned) } },
        onArchive = { ids, archived ->
            scope.launch {
                val before = withContext(Dispatchers.IO) { flagStore.setArchived(ids, archived) }
                val message = resources.getQuantityString(
                    if (archived) R.plurals.archived_count else R.plurals.unarchived_count,
                    ids.size,
                    ids.size,
                )
                undo.push(message) { flagStore.restore(before) }
            }
        },
        onBlock = { chosen ->
            blockConversations(scope, undo, resources, context, chosen.map { Triple(it.threadId, it.address, it.title) })
        },
        onDelete = { chosen ->
            deleteConversations(scope, trash, undo, resources, chosen.associate { it.threadId to it.address }, context)
        },
        onBack = onBack,
        onNewMessage = onNewMessage,
        onShowArchive = onShowArchive,
        onShowTrash = onShowTrash,
        onSettings = onSettings,
        onSearch = onSearch,
    )
}

/** Moves conversations to the trash, clears their notifications, and offers undo. */
private fun deleteConversations(
    scope: CoroutineScope,
    trash: Trash,
    undo: UndoQueue,
    resources: Resources,
    threads: Map<Long, String>,
    context: Context,
) {
    val notifier = MessageNotifier(context)
    scope.launch {
        val items = withContext(Dispatchers.IO) { trash.deleteConversations(threads) }
        if (items.isEmpty()) return@launch
        threads.keys.forEach { notifier.cancel(it) }
        undo.push(resources.getQuantityString(R.plurals.deleted_conversations, items.size, items.size)) {
            trash.restore(items.asReversed())
        }
    }
}

/**
 * Blocks the senders of conversations, given as thread, address and title; marks what they
 * sent read, as a message arriving while blocked is, clears their notifications, and offers
 * undo.
 */
private fun blockConversations(
    scope: CoroutineScope,
    undo: UndoQueue,
    resources: Resources,
    context: Context,
    threads: List<Triple<Long, String, String>>,
) {
    val blockList = BlockList.get(context)
    val messages = MessageRepository(context)
    val notifier = MessageNotifier(context)
    scope.launch {
        withContext(Dispatchers.IO) {
            threads.forEach { (threadId, address) ->
                blockList.block(address)
                messages.markRead(threadId)
            }
        }
        threads.forEach { notifier.cancel(it.first) }
        val message = threads.singleOrNull()?.let { resources.getString(R.string.blocked_done, it.third) }
            ?: resources.getQuantityString(R.plurals.blocked_count, threads.size, threads.size)
        undo.push(message) { threads.forEach { blockList.unblock(it.second) } }
    }
}

/** How long typing must pause before a search runs, so each key press does not query. */
private const val SEARCH_DELAY_MILLIS = 150L

@Composable
private fun SearchRoute(
    repository: ConversationRepository,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (SearchResult) -> Unit,
    onBack: () -> Unit,
) {
    // The previous results stay up while the next search runs, so the list does not blink
    // empty on every key press.
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(SEARCH_DELAY_MILLIS)
        results = withContext(Dispatchers.IO) { repository.search(query) }
    }
    SearchScreen(query, onQueryChange, results, onOpen, onBack)
}

@Composable
private fun BlockedRoute(
    repository: ConversationRepository,
    undo: UndoQueue,
    onOpen: (Conversation) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val blockList = remember { BlockList.get(context) }
    val flow = remember { repository.blocked() }
    val entries by flow.collectAsStateWithLifecycle(initialValue = null)
    BlockedScreen(
        entries = entries,
        onOpen = onOpen,
        onUnblock = { entry ->
            scope.launch {
                withContext(Dispatchers.IO) { blockList.unblock(entry.address) }
                undo.push(resources.getString(R.string.unblocked_done, entry.title)) { blockList.block(entry.address) }
            }
        },
        onBack = onBack,
    )
}

/** The schedule picker is open: to change [message]'s time, or for a new one when null. */
private data class SchedulePick(val message: PendingMessage?)

/** Sends a waiting message now, or cancels it with undo. */
private fun pendingAction(
    scope: CoroutineScope,
    context: Context,
    undo: UndoQueue,
    resources: Resources,
    message: PendingMessage,
    action: PendingAction,
) {
    val outgoing = Outgoing.get(context)
    when (action) {
        PendingAction.SendNow -> scope.launch(Dispatchers.IO) { OutgoingQueue.sendNow(context, message.id) }
        PendingAction.Cancel -> scope.launch {
            val claimed = withContext(Dispatchers.IO) { outgoing.claim(message.id) } ?: return@launch
            undo.push(resources.getString(R.string.scheduled_cancelled)) { outgoing.restore(claimed) }
        }
        else -> Unit
    }
}

@Composable
private fun ScheduledRoute(undo: UndoQueue, onOpen: (PendingMessage) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val contacts = remember { ContactRepository.get(context) }
    val flow = remember {
        Outgoing.get(context).all()
            .map { all -> all.filter { !it.delayed }.map { ScheduledEntry(it, contacts.lookup(it.address)) } }
            .flowOn(Dispatchers.IO)
    }
    val entries by flow.collectAsStateWithLifecycle(initialValue = null)
    ScheduledScreen(
        entries = entries,
        onOpen = onOpen,
        onAction = { message, action -> pendingAction(scope, context, undo, resources, message, action) },
        onBack = onBack,
    )
}

@Composable
private fun TrashRoute(undo: UndoQueue, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val trash = remember { Trash.get(context) }
    val flow = remember { trash.items() }
    val items by flow.collectAsStateWithLifecycle(initialValue = null)
    TrashScreen(
        items = items,
        onRestore = { item ->
            scope.launch(Dispatchers.IO) { trash.restore(listOf(item.id)) }
            undo.snackbar.currentSnackbarData?.dismiss()
            scope.launch { undo.snackbar.showSnackbar(resources.getQuantityString(R.plurals.restored_count, 1, 1)) }
        },
        onDeleteForever = { item -> scope.launch(Dispatchers.IO) { trash.deleteForever(listOf(item.id)) } },
        onEmpty = { scope.launch(Dispatchers.IO) { trash.empty() } },
        onBack = onBack,
    )
}

/** Starts another app's screen, doing nothing when no app on the phone handles it. */
private fun openExternal(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * Opens a saved contact in the contacts app, or offers to save [address] as a new one.
 * QuickContact opens work profile contacts too, which a plain view intent cannot reach.
 */
private fun openContact(context: Context, contact: Contact?, address: String) {
    val uri = contact?.lookupUri
    if (uri != null) {
        try {
            ContactsContract.QuickContact.showQuickContact(context, Rect(), uri, ContactsContract.QuickContact.MODE_LARGE, null)
        } catch (_: ActivityNotFoundException) {
            openExternal(context, Intent(Intent.ACTION_VIEW, uri))
        }
    } else {
        openExternal(
            context,
            Intent(ContactsContract.Intents.Insert.ACTION)
                .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.PHONE, address),
        )
    }
}

/**
 * Puts a one-time code on the clipboard, marked sensitive so that Android 13 and later keep
 * it out of the clipboard preview and keyboard suggestions.
 */
private fun copyCode(context: Context, code: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.one_time_code), code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

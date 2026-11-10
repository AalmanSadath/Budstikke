package dev.aalman.budstikke

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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import dev.aalman.budstikke.data.Conversation
import dev.aalman.budstikke.data.ConversationFlags
import dev.aalman.budstikke.data.Flags
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
import dev.aalman.budstikke.ui.SettingsScreen
import dev.aalman.budstikke.ui.theme.BudstikkeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A conversation open on screen: its thread in the message store and who it is with. */
private data class OpenThread(val threadId: Long, val address: String)

class MainActivity : ComponentActivity() {

    private val roleManager by lazy { getSystemService(RoleManager::class.java) }
    private var isDefaultSmsApp by mutableStateOf(false)
    private var openThread by mutableStateOf<OpenThread?>(null)
    private var showSettings by mutableStateOf(false)
    private var showArchive by mutableStateOf(false)
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

        val conversations = ConversationRepository(this)
        val messages = MessageRepository(this)
        val sender = SmsSender(this)
        val roleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_SMS)

        setContent {
            BudstikkeTheme {
                val thread = openThread
                when {
                    !isDefaultSmsApp -> RoleScreen(roleAvailable) {
                        requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    }
                    thread != null -> ConversationRoute(thread, messages, sender, sims) { openThread = null }
                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(sims = sims, onBack = { showSettings = false })
                    }
                    showArchive -> {
                        BackHandler { showArchive = false }
                        ConversationListRoute(
                            conversations,
                            archive = true,
                            onOpen = { openThread = OpenThread(it.threadId, it.address) },
                            onBack = { showArchive = false },
                        )
                    }
                    else -> ConversationListRoute(
                        conversations,
                        archive = false,
                        onOpen = { openThread = OpenThread(it.threadId, it.address) },
                        onNewMessage = { startActivity(Intent(this, ComposeActivity::class.java)) },
                        onShowArchive = { showArchive = true },
                        onSettings = { showSettings = true },
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

        /** Opens one conversation, reusing the activity if it is already running. */
        fun openConversation(context: Context, threadId: Long, address: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_THREAD_ID, threadId)
                .putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun ConversationRoute(
    thread: OpenThread,
    repository: MessageRepository,
    sender: SmsSender,
    sims: List<Sim>,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(thread.threadId) { repository.messages(thread.threadId) }
    val messages by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var text by rememberSaveable(thread.threadId) { mutableStateOf("") }
    var chosenSubscription by rememberSaveable(thread.threadId) { mutableStateOf<Int?>(null) }
    val settings by SettingsRepository.get(context).settings.collectAsState()
    val sim = sims.firstOrNull { it.subscriptionId == chosenSubscription }
        ?: preferredSim(sims, settings.defaultSubscriptionId)
    val contacts = remember { ContactRepository.get(context) }
    val flagStore = remember { ConversationFlags.get(context) }
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
                Lifecycle.Event.ON_PAUSE -> ActiveConversation.threadId = null
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (ActiveConversation.threadId == thread.threadId) ActiveConversation.threadId = null
        }
    }
    LaunchedEffect(thread.threadId, messages.size) {
        repository.markRead(thread.threadId)
        MessageNotifier(context).cancel(thread.threadId)
    }

    ConversationScreen(
        address = thread.address,
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
                scope.launch(Dispatchers.IO) { sender.send(thread.address, body, subscription) }
            }
        },
        onRetry = { message -> scope.launch(Dispatchers.IO) { sender.retry(message.id) } },
        flags = flags,
        onPinChange = { pinned ->
            scope.launch(Dispatchers.IO) { flagStore.setPinned(listOf(thread.threadId), pinned) }
        },
        onArchiveChange = { archived ->
            scope.launch(Dispatchers.IO) { flagStore.setArchived(listOf(thread.threadId), archived) }
            // An archived conversation leaves the list it was opened from, so go back to it.
            if (archived) onBack()
        },
        onBack = onBack,
    )
}

/**
 * The main list or the archive, with pinning and archiving. Archiving or unarchiving from
 * the list offers undo, which puts pins back too.
 */
@Composable
private fun ConversationListRoute(
    repository: ConversationRepository,
    archive: Boolean,
    onOpen: (Conversation) -> Unit,
    onBack: (() -> Unit)? = null,
    onNewMessage: (() -> Unit)? = null,
    onShowArchive: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flagStore = remember { ConversationFlags.get(context) }
    val flow = remember(archive) { repository.conversations(archived = archive) }
    val list by flow.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    ConversationListScreen(
        conversations = list,
        archive = archive,
        snackbar = snackbar,
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
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message,
                    actionLabel = resources.getString(R.string.undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    withContext(Dispatchers.IO) { flagStore.restore(before) }
                }
            }
        },
        onBack = onBack,
        onNewMessage = onNewMessage,
        onShowArchive = onShowArchive,
        onSettings = onSettings,
    )
}

package dev.aalman.budstikke

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.ContactMatch
import dev.aalman.budstikke.data.ContactRepository
import dev.aalman.budstikke.data.Outgoing
import dev.aalman.budstikke.ui.SchedulePicker
import dev.aalman.budstikke.sms.looksLikeNumber
import dev.aalman.budstikke.sms.typedRecipient
import dev.aalman.budstikke.sms.withRecipient
import dev.aalman.budstikke.ui.Avatar
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.aalman.budstikke.ui.clearFocusOnTap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.aalman.budstikke.settings.SettingsRepository
import dev.aalman.budstikke.sms.OutgoingQueue
import dev.aalman.budstikke.sms.activeSims
import dev.aalman.budstikke.sms.bodyOf
import dev.aalman.budstikke.sms.parseRecipients
import dev.aalman.budstikke.sms.preferredSim
import dev.aalman.budstikke.ui.ComposeBar
import dev.aalman.budstikke.ui.TopBar
import dev.aalman.budstikke.ui.theme.BudstikkeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A new message: from the new-message button, or from an sms:/smsto: link in another app,
 * which can carry the numbers and a body. Several numbers each get the message on their own,
 * since group messages need MMS.
 */
class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only the default SMS app may write sent messages, so without the role the main
        // screen asks for it first.
        if (!getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        val ssp = intent.data?.schemeSpecificPart.orEmpty()
        val initialTo = parseRecipients(ssp).joinToString(", ")
        val initialBody = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: bodyOf(ssp)
            ?: ""
        val sims = activeSims(this)
        val contacts = ContactRepository.get(this)

        setContent {
            BudstikkeTheme {
                // A TextFieldValue so that picking a suggestion can put the cursor after it.
                var to by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue(initialTo, TextRange(initialTo.length)))
                }
                var dialpad by rememberSaveable { mutableStateOf(false) }
                var notNumber by remember { mutableStateOf(false) }
                val typed = typedRecipient(to.text)
                var suggestions by remember { mutableStateOf(emptyList<ContactMatch>()) }
                LaunchedEffect(typed) {
                    delay(SUGGEST_DELAY_MILLIS)
                    suggestions = withContext(Dispatchers.IO) { contacts.search(typed) }
                }
                val pick = { number: String ->
                    val text = withRecipient(to.text, number)
                    to = TextFieldValue(text, TextRange(text.length))
                    notNumber = false
                }
                var body by rememberSaveable { mutableStateOf(initialBody) }
                var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
                val settings by SettingsRepository.get(this).settings.collectAsState()
                val sim = sims.firstOrNull { it.subscriptionId == chosen }
                    ?: preferredSim(sims, settings.defaultSubscriptionId)
                var scheduling by rememberSaveable { mutableStateOf(false) }
                val subscription = sim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                // The recipients when there is something to send to them, or null. A name typed
                // but not picked has no number to send to, and says so under the field.
                val sendable = {
                    val recipients = parseRecipients(to.text)
                    when {
                        recipients.any { !looksLikeNumber(it) } -> {
                            notNumber = true
                            null
                        }
                        recipients.isEmpty() || body.isBlank() -> null
                        else -> recipients
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .imePadding(),
                ) {
                    TopBar(stringResource(R.string.new_message), onBack = ::finish)
                    OutlinedTextField(
                        value = to,
                        onValueChange = {
                            to = it
                            notNumber = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        label = { Text(stringResource(R.string.to_label)) },
                        placeholder = { Text(stringResource(R.string.to_hint)) },
                        singleLine = true,
                        isError = notNumber,
                        supportingText = if (notNumber) ({ Text(stringResource(R.string.to_not_number)) }) else null,
                        // Names need letters, so the keyboard is the default; the number pad
                        // is a tap away for typing a number.
                        trailingIcon = {
                            IconButton(onClick = { dialpad = !dialpad }) {
                                Icon(
                                    painterResource(if (dialpad) R.drawable.ic_keyboard else R.drawable.ic_dialpad),
                                    stringResource(if (dialpad) R.string.use_keyboard else R.string.use_dialpad),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    // Below the field: who the typed text could be. Tapping elsewhere in
                    // the space takes the cursor away.
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().clearFocusOnTap(LocalFocusManager.current)) {
                        if (looksLikeNumber(typed) && suggestions.none { it.number == typed }) {
                            item(key = "number") {
                                SuggestionRow(
                                    title = stringResource(R.string.send_to_number, typed),
                                    detail = null,
                                    address = typed,
                                    contact = null,
                                    onClick = { pick(typed) },
                                )
                            }
                        }
                        items(suggestions, key = { "${it.name}|${it.number}" }) { match ->
                            SuggestionRow(
                                title = match.name,
                                detail = "${match.label} · ${match.number}",
                                address = match.number,
                                contact = match.contact,
                                onClick = { pick(match.number) },
                            )
                        }
                    }
                    ComposeBar(
                        text = body,
                        onTextChange = { body = it },
                        sims = sims,
                        sim = sim,
                        onSimChange = { chosen = it.subscriptionId },
                        onSend = {
                            val recipients = sendable()
                            if (recipients != null) send(recipients, body.trim(), subscription)
                        },
                        modifier = Modifier.navigationBarsPadding(),
                        onSchedule = { if (sendable() != null) scheduling = true },
                    )
                    if (scheduling) {
                        SchedulePicker(
                            initialMillis = null,
                            onDismiss = { scheduling = false },
                            onPick = { millis ->
                                scheduling = false
                                val recipients = sendable()
                                if (recipients != null) send(recipients, body.trim(), subscription, at = millis)
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** How long typing in "To" must pause before contacts are searched. */
        const val SUGGEST_DELAY_MILLIS = 100L
    }

    /** Sends [text] to each recipient now, or schedules it for [at] when that is given. */
    private fun send(recipients: List<String>, text: String, subscriptionId: Int, at: Long? = null) {
        lifecycleScope.launch {
            val thread = withContext(Dispatchers.IO) {
                // Each recipient gets their own conversation, which shows the send delay's
                // countdown or the scheduled message.
                val outgoing = Outgoing.get(this@ComposeActivity)
                recipients.forEach { recipient ->
                    val thread = Telephony.Threads.getOrCreateThreadId(this@ComposeActivity, recipient)
                    if (at == null) {
                        OutgoingQueue.sendOrHold(this@ComposeActivity, thread, recipient, text, subscriptionId)
                    } else {
                        outgoing.add(thread, recipient, text, subscriptionId, at, delayed = false)
                    }
                }
                recipients.singleOrNull()?.let { Telephony.Threads.getOrCreateThreadId(this@ComposeActivity, it) }
            }
            startActivity(
                if (thread != null) {
                    MainActivity.openConversation(this@ComposeActivity, thread, recipients.single())
                } else {
                    Intent(this@ComposeActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
            finish()
        }
    }
}

/** A contact, or a typed number, to send to. */
@Composable
private fun SuggestionRow(title: String, detail: String?, address: String, contact: Contact?, onClick: () -> Unit) {
    val work = if (contact?.work == true) stringResource(R.string.work_contact) else null
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, work, detail).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(address, contact, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

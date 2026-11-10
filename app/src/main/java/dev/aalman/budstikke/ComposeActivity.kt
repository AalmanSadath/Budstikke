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
import dev.aalman.budstikke.sms.SmsSender
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
        val sender = SmsSender(this)

        setContent {
            BudstikkeTheme {
                var to by rememberSaveable { mutableStateOf(initialTo) }
                var body by rememberSaveable { mutableStateOf(initialBody) }
                var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
                val settings by SettingsRepository.get(this).settings.collectAsState()
                val sim = sims.firstOrNull { it.subscriptionId == chosen }
                    ?: preferredSim(sims, settings.defaultSubscriptionId)

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .imePadding(),
                ) {
                    TopBar(stringResource(R.string.new_message), onBack = ::finish)
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        label = { Text(stringResource(R.string.to_label)) },
                        placeholder = { Text(stringResource(R.string.to_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    )
                    // The empty space between the fields; tapping it takes the cursor away.
                    Box(Modifier.weight(1f).fillMaxWidth().clearFocusOnTap(LocalFocusManager.current))
                    ComposeBar(
                        text = body,
                        onTextChange = { body = it },
                        sims = sims,
                        sim = sim,
                        onSimChange = { chosen = it.subscriptionId },
                        onSend = {
                            val recipients = parseRecipients(to)
                            val text = body.trim()
                            if (recipients.isNotEmpty() && text.isNotEmpty()) {
                                send(sender, recipients, text, sim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                            }
                        },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        }
    }

    private fun send(sender: SmsSender, recipients: List<String>, text: String, subscriptionId: Int) {
        lifecycleScope.launch {
            val thread = withContext(Dispatchers.IO) {
                recipients.forEach { sender.send(it, text, subscriptionId) }
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

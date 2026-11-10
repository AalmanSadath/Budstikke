package dev.aalman.budstikke

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.ui.TopBar
import dev.aalman.budstikke.ui.theme.BudstikkeTheme

/**
 * Opened by sms: and smsto: links. The SMS role requires an activity for them; for now it
 * names the recipient, and sending comes later.
 */
class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val recipients = recipientsOf(intent)

        setContent {
            BudstikkeTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    TopBar(stringResource(R.string.compose_title))
                    Column(Modifier.padding(16.dp)) {
                        if (recipients.isNotEmpty()) {
                            Text(
                                stringResource(R.string.compose_to, recipients),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            stringResource(R.string.compose_placeholder),
                            Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    /** "sms:+4712345678;+4787654321?body=hi" gives "+4712345678, +4787654321". */
    private fun recipientsOf(intent: Intent): String {
        val data: Uri = intent.data ?: return ""
        return data.schemeSpecificPart.orEmpty()
            .substringBefore('?')
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }
}

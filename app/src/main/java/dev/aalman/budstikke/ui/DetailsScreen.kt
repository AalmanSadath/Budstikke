package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.Flags

/**
 * Who a conversation is with, and what can be done about them: call, see or save the
 * contact, block or unblock the number, archive the conversation. Calling and saving are
 * left out for a sender name such as "BANK", which is not a number.
 */
@Composable
fun DetailsScreen(
    address: String,
    contact: Contact?,
    flags: Flags,
    blocked: Boolean,
    onCall: () -> Unit,
    onContact: () -> Unit,
    onBlockChange: (Boolean) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val isNumber = address.any(Char::isDigit)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.details), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 4.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(address, contact, size = 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                contact?.name ?: address,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (contact != null) {
                Text(address, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (contact?.work == true) {
                Text(stringResource(R.string.work_contact), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNumber) {
                    ActionRow(R.drawable.ic_call, stringResource(R.string.call), onClick = onCall)
                    if (contact?.lookupUri != null) {
                        ActionRow(R.drawable.ic_person, stringResource(R.string.view_contact), onClick = onContact)
                    } else if (contact == null) {
                        ActionRow(R.drawable.ic_person_add, stringResource(R.string.add_contact), onClick = onContact)
                    }
                }
                // A blocked conversation lives only in Spam & blocked, so the archive has
                // nothing to act on until it is unblocked.
                if (!blocked) {
                    ActionRow(
                        if (flags.archived) R.drawable.ic_unarchive else R.drawable.ic_archive,
                        stringResource(if (flags.archived) R.string.unarchive else R.string.archive),
                        onClick = { onArchiveChange(!flags.archived) },
                    )
                }
                ActionRow(
                    R.drawable.ic_block,
                    stringResource(if (blocked) R.string.unblock_number else R.string.block_number),
                    danger = !blocked,
                    onClick = { onBlockChange(!blocked) },
                )
            }
        }
    }
}

@Composable
private fun ActionRow(icon: Int, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colour = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .card()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colour)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = colour)
    }
}

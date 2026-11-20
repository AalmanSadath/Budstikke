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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.ui.theme.touchTarget
import dev.aalman.budstikke.data.Flags
import dev.aalman.budstikke.data.Member
import dev.aalman.budstikke.data.membersTitle

/**
 * Who a conversation is with, and what can be done about them: call, see or save the
 * contact, block or unblock the number, archive the conversation. Calling and saving are
 * left out for a sender name such as "BANK", which is not a number. A group lists its
 * members, each opening their contact; it is never called or blocked as a whole.
 */
@Composable
fun DetailsScreen(
    members: List<Member>,
    flags: Flags,
    blocked: Boolean,
    onCall: () -> Unit,
    onContact: (Member) -> Unit,
    onBlockChange: (Boolean) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val group = members.size > 1
    val one = members.firstOrNull() ?: Member("")
    val contact = one.contact
    val isNumber = !group && one.address.any(Char::isDigit)
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
            MembersAvatar(members, size = 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                membersTitle(members),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!group && contact != null) {
                Text(one.address, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!group && contact?.work == true) {
                Text(stringResource(R.string.work_contact), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNumber) {
                    ActionRow(R.drawable.ic_call, stringResource(R.string.call), onClick = onCall)
                    if (contact?.lookupUri != null) {
                        ActionRow(R.drawable.ic_person, stringResource(R.string.view_contact), onClick = { onContact(one) })
                    } else if (contact == null) {
                        ActionRow(R.drawable.ic_person_add, stringResource(R.string.add_contact), onClick = { onContact(one) })
                    }
                }
                if (group) {
                    Text(
                        pluralStringResource(R.plurals.group_members, members.size, members.size),
                        Modifier.padding(horizontal = 16.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    members.forEach { MemberRow(it, onClick = { onContact(it) }) }
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
                if (!group) {
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
}

/** One member of a group: avatar, name and number. Tapping opens or adds their contact. */
@Composable
private fun MemberRow(member: Member, onClick: () -> Unit) {
    val work = if (member.contact?.work == true) stringResource(R.string.work_contact) else null
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .card()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = touchTarget(52.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(member.name, work, member.address.takeIf { member.contact != null }).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(member.address, member.contact, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (member.contact != null) {
                Text(member.address, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .heightIn(min = touchTarget(52.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colour)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = colour)
    }
}

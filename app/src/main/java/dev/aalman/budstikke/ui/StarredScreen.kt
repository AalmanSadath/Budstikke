package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.LockState
import dev.aalman.budstikke.data.StarredMessage

/**
 * Every starred message, newest star first. A card opens its conversation at the message;
 * the star on it takes the star off. Locked text stays hidden here too.
 */
@Composable
fun StarredScreen(
    entries: List<StarredMessage>?,
    locks: LockState,
    unlocked: Boolean,
    onOpen: (StarredMessage) -> Unit,
    onUnstar: (StarredMessage) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.starred), onBack = onBack)
        when {
            entries == null -> Unit
            entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(
                    stringResource(R.string.starred_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.add(WindowInsets(top = 8.dp, bottom = 8.dp)).asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    StarredRow(entry, hidden = !unlocked && locks.isLocked(entry.threadId, entry.id), onOpen, onUnstar)
                }
            }
        }
    }
}

@Composable
private fun StarredRow(
    entry: StarredMessage,
    hidden: Boolean,
    onOpen: (StarredMessage) -> Unit,
    onUnstar: (StarredMessage) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .card()
            .clickable { onOpen(entry) }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(entry.address, entry.contact, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (entry.fromMe) stringResource(R.string.you) else entry.title,
                    Modifier.weight(1f, fill = false),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.fromMe) {
                    Text(
                        " → ${entry.title}",
                        Modifier.weight(1f, fill = false),
                        fontSize = 15.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(listTime(context, entry.dateMillis), fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Text(
                if (hidden) stringResource(R.string.locked_message) else entry.body,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                fontStyle = if (hidden) FontStyle.Italic else FontStyle.Normal,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onUnstar(entry) }) {
            Icon(painterResource(R.drawable.ic_star), stringResource(R.string.unstar), tint = colors.primary)
        }
    }
}

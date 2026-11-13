package dev.aalman.budstikke.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.sms.Sim

/**
 * Which slot each SIM sits in, by subscription id, for the badges beside conversation
 * names. Empty when the badges are off or only one SIM is active, so there is nothing to
 * tell apart.
 */
fun simSlots(sims: List<Sim>, enabled: Boolean): Map<Int, Int> =
    if (enabled && sims.size > 1) sims.associate { it.subscriptionId to it.slot } else emptyMap()

/**
 * A SIM card with its slot number in the middle, as drawn beside a conversation's name.
 * [size] is at the system's normal text size; it grows with larger text, up to half again.
 */
@Composable
fun SimBadge(slot: Int, size: Dp, modifier: Modifier = Modifier) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    val description = stringResource(R.string.sim_badge, slot)
    Box(
        modifier
            .size(size * scale)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(painterResource(R.drawable.ic_sim_card), contentDescription = null, Modifier.fillMaxSize(), tint = color)
        // Sized to the badge rather than the text setting, which already scaled the badge;
        // a little low, as the card's cut corner takes room from the top.
        val digit = with(LocalDensity.current) { (size * scale * 0.42f).toSp() }
        Text(
            slot.toString(),
            Modifier.padding(top = size * scale / 10),
            fontSize = digit,
            lineHeight = digit,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

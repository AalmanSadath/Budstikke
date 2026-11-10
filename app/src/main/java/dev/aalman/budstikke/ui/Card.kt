package dev.aalman.budstikke.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(10.dp)

/**
 * After BifroSSH's host card (hosts.css): a 1 dp border and 10 dp corners, left clear so
 * the page shows through instead of BifroSSH's panel colour. A selected card gets a 2 dp accent border instead of a fill, as
 * BifroSSH marks cards picked for a bulk action. Goes before the click modifier, so the
 * press ripple stays inside the corners.
 */
@Composable
fun Modifier.card(selected: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .clip(CardShape)
        .border(
            if (selected) 2.dp else 1.dp,
            if (selected) colors.primary else colors.outlineVariant,
            CardShape,
        )
}

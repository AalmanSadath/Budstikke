package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.sms.Sim

/**
 * The message field with its send button. With more than one active SIM, the empty field's
 * hint is the saved name of the SIM the message will go out on, and long-pressing the field
 * lists the other SIMs to switch to.
 */
@Composable
fun ComposeBar(
    text: String,
    onTextChange: (String) -> Unit,
    sims: List<Sim>,
    sim: Sim?,
    onSimChange: (Sim) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = text.isNotBlank()
    val switchLabel = stringResource(R.string.send_with_sim)
    // The long-press menu is built from a lambda the text field holds on to, so it reads the
    // SIMs through these to see the current choice rather than the one from its first use.
    val currentSims by rememberUpdatedState(sims)
    val currentSim by rememberUpdatedState(sim)
    val currentOnSimChange by rememberUpdatedState(onSimChange)
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .heightIn(min = 48.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (text.isEmpty()) {
                    Text(
                        // With several SIMs the hint names the one this message goes out on.
                        if (sims.size > 1 && sim != null) sim.name else stringResource(R.string.message_hint),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .appendTextContextMenuComponents {
                            // Long-pressing the field offers the other SIMs next to Paste.
                            val others = currentSims.filter { it.subscriptionId != currentSim?.subscriptionId }
                            if (currentSims.size > 1 && others.isNotEmpty()) {
                                separator()
                                others.forEach { other ->
                                    item(key = other.subscriptionId, label = switchLabel.format(other.name)) {
                                        currentOnSimChange(other)
                                        close()
                                    }
                                }
                            }
                        },
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.send))
        }
    }
}

package dev.aalman.budstikke.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import dev.aalman.budstikke.R
import dev.aalman.budstikke.ui.theme.touchTarget
import dev.aalman.budstikke.sms.Sim

/**
 * The message field with its send button. With more than one active SIM, the empty field's
 * hint is the saved name of the SIM the message will go out on. Long-pressing send opens a
 * menu with the other SIMs to switch to and, where it is offered, scheduling; a notch in
 * the button's corner shows the menu is there.
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
    onSchedule: (() -> Unit)? = null,
) {
    val canSend = text.isNotBlank()
    val otherSims = if (sims.size > 1) sims.filter { it.subscriptionId != sim?.subscriptionId } else emptyList()
    val hasMenu = otherSims.isNotEmpty() || onSchedule != null
    var menu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
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
                .heightIn(min = touchTarget(48.dp))
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
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Drawn by hand rather than as a FilledIconButton, which has no long press. A tap
        // sends only with text to send; the long press works on an empty field too, so the
        // SIM can be chosen before typing. Disabled colours are M3's.
        val colors = MaterialTheme.colorScheme
        val content = if (canSend) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f)
        Box {
            Box(
                Modifier
                    .size(touchTarget(48.dp))
                    .clip(CircleShape)
                    .background(if (canSend) colors.primary else colors.onSurface.copy(alpha = 0.12f))
                    .combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = if (hasMenu) stringResource(R.string.more_options) else null,
                        onLongClick = if (hasMenu) ({ menu = true }) else null,
                        onClick = { if (canSend) onSend() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.send), tint = content)
                if (hasMenu) {
                    // A small corner notch, like the mark on keyboard keys with more behind a
                    // long press, tucked inside the circle's top right.
                    Canvas(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 11.dp, top = 11.dp)
                            .size(7.dp),
                    ) {
                        val notch = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(notch, content)
                    }
                }
            }
            // The menu does not take focus, so the field keeps it and the keyboard stays up
            // while choosing; a focusable menu would close the keyboard and jump as the
            // screen resized. Without focus it cannot see Back, so Back is caught here.
            BackHandler(enabled = menu) { menu = false }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                properties = PopupProperties(focusable = false),
            ) {
                otherSims.forEach { other ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.send_with_sim, other.name)) },
                        onClick = {
                            menu = false
                            onSimChange(other)
                        },
                    )
                }
                if (onSchedule != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_send)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_schedule), null) },
                        enabled = canSend,
                        onClick = {
                            menu = false
                            // The pickers take the whole screen's attention: the keyboard goes.
                            focusManager.clearFocus()
                            onSchedule()
                        },
                    )
                }
            }
        }
    }
}

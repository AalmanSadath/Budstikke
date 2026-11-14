package dev.aalman.budstikke.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.ui.layout.ContentScale
import androidx.core.net.toUri
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
    /** Pictures going with the message, as URIs; with any, it is sent as an MMS. */
    attachments: List<String> = emptyList(),
    /** Opens the photo picker; null where pictures cannot be sent. */
    onAttach: (() -> Unit)? = null,
    onRemoveAttachment: (String) -> Unit = {},
) {
    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    val otherSims = if (sims.size > 1) sims.filter { it.subscriptionId != sim?.subscriptionId } else emptyList()
    val hasMenu = otherSims.isNotEmpty() || onSchedule != null
    var menu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (attachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { uri -> AttachmentThumbnail(uri, onRemove = { onRemoveAttachment(uri) }) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
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
                if (onAttach != null) {
                    IconButton(onClick = onAttach, modifier = Modifier.size(touchTarget(40.dp))) {
                        Icon(
                            painterResource(R.drawable.ic_image),
                            contentDescription = stringResource(R.string.attach_photos),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                } else {
                    Spacer(Modifier.width(12.dp))
                }
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
}

/** A picture waiting to be sent, with a button that takes it off the message. */
@Composable
private fun AttachmentThumbnail(uri: String, onRemove: () -> Unit) {
    val image = rememberDecoded(uri.toUri(), THUMBNAIL_PX)
    Box(Modifier.size(72.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            image?.let {
                Image(it, contentDescription = stringResource(R.string.attachment_photo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.remove_attachment), onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.remove_attachment),
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val THUMBNAIL_PX = 256

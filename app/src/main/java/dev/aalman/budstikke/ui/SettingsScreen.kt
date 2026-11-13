package dev.aalman.budstikke.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.telephony.SubscriptionManager
import dev.aalman.budstikke.R
import dev.aalman.budstikke.ui.theme.touchTarget
import dev.aalman.budstikke.settings.SEND_DELAYS
import dev.aalman.budstikke.settings.LauncherName
import dev.aalman.budstikke.settings.Accessibility
import androidx.compose.foundation.selection.toggleable
import dev.aalman.budstikke.settings.SettingsRepository
import dev.aalman.budstikke.settings.ThemeMode
import dev.aalman.budstikke.settings.ThemeSource
import dev.aalman.budstikke.sms.Sim
import dev.aalman.budstikke.ui.theme.dynamicColourAvailable
import dev.aalman.budstikke.ui.theme.parseHex
import dev.aalman.budstikke.ui.theme.toHex

/** Accents offered as swatches: BifroSSH's three, then a spread around the wheel. */
internal val accentSwatches = listOf(
    0x58A6FF, 0xA78BFA, 0x155397, 0x10B981, 0x84CC16,
    0xF59E0B, 0xEF4444, 0xEC4899, 0x06B6D4, 0x8B5CF6,
)

@Composable
fun SettingsScreen(sims: List<Sim>, onShowScheduled: () -> Unit, onShowBlocked: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = SettingsRepository.get(context)
    val settings by repository.settings.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(stringResource(R.string.settings), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            SectionHeading(stringResource(R.string.settings_appearance))

            Label(stringResource(R.string.settings_theme))
            Choices(
                options = listOf(
                    ThemeSource.Dynamic to stringResource(R.string.settings_theme_dynamic),
                    ThemeSource.Bifrossh to stringResource(R.string.settings_theme_custom),
                ),
                selected = if (dynamicColourAvailable) settings.themeSource else ThemeSource.Bifrossh,
                enabled = { it != ThemeSource.Dynamic || dynamicColourAvailable },
                onSelect = { source -> repository.update { it.copy(themeSource = source) } },
            )

            Spacer(Modifier.height(16.dp))
            Label(stringResource(R.string.settings_mode))
            Choices(
                options = listOf(
                    ThemeMode.System to stringResource(R.string.settings_mode_system),
                    ThemeMode.Light to stringResource(R.string.settings_mode_light),
                    ThemeMode.Dark to stringResource(R.string.settings_mode_dark),
                ),
                selected = settings.themeMode,
                onSelect = { mode -> repository.update { it.copy(themeMode = mode) } },
            )

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.settings_true_black),
                hint = null,
                checked = settings.trueBlack,
                onCheckedChange = { on -> repository.update { it.copy(trueBlack = on) } },
            )

            Spacer(Modifier.height(16.dp))
            Label(stringResource(R.string.settings_accent))
            AccentPicker(
                accent = settings.accent,
                onAccent = { accent -> repository.update { it.copy(accent = accent) } },
            )

            Spacer(Modifier.height(16.dp))
            Label(stringResource(R.string.settings_launcher_name))
            var messagesName by remember { mutableStateOf(LauncherName.isMessages(context)) }
            var confirmName by remember { mutableStateOf<Boolean?>(null) }
            Choices(
                options = listOf(false to stringResource(R.string.app_name), true to stringResource(R.string.launcher_messages)),
                selected = messagesName,
                onSelect = { messages -> if (messages != messagesName) confirmName = messages },
            )
            // Some launchers drop a home-screen icon when the name changes; say so first.
            confirmName?.let { messages ->
                AlertDialog(
                    onDismissRequest = { confirmName = null },
                    title = { Text(stringResource(R.string.settings_launcher_name)) },
                    text = { Text(stringResource(R.string.settings_launcher_hint)) },
                    confirmButton = {
                        TextButton(onClick = {
                            LauncherName.set(context, messages)
                            messagesName = messages
                            confirmName = null
                        }) { Text(stringResource(R.string.settings_launcher_change)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmName = null }) { Text(stringResource(R.string.cancel)) } },
                )
            }

            Spacer(Modifier.height(28.dp))
            AccessibilitySection(settings.accessibility) { a11y -> repository.update { it.copy(accessibility = a11y) } }

            Spacer(Modifier.height(28.dp))
            SectionHeading(stringResource(R.string.settings_sending))
            Label(stringResource(R.string.settings_sim))
            if (sims.isEmpty()) {
                Hint(stringResource(R.string.settings_sim_none))
            } else {
                val chosen = settings.defaultSubscriptionId.takeIf { id -> sims.any { it.subscriptionId == id } }
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                Column(Modifier.selectableGroup()) {
                    RadioRow(stringResource(R.string.settings_sim_system), null, chosen == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        repository.update { it.copy(defaultSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID) }
                    }
                    sims.forEach { sim ->
                        RadioRow(sim.name, sim.slotLabel, chosen == sim.subscriptionId) {
                            repository.update { it.copy(defaultSubscriptionId = sim.subscriptionId) }
                        }
                    }
                }
            }
            if (sims.size > 1) {
                SwitchRow(
                    title = stringResource(R.string.settings_sim_badge),
                    hint = null,
                    checked = settings.showSimBadge,
                    onCheckedChange = { on -> repository.update { it.copy(showSimBadge = on) } },
                )
            }
            Spacer(Modifier.height(16.dp))
            Label(stringResource(R.string.settings_send_delay))
            Choices(
                options = SEND_DELAYS.map { seconds ->
                    seconds to if (seconds == 0) stringResource(R.string.settings_send_delay_off) else stringResource(R.string.seconds_short, seconds)
                },
                selected = settings.sendDelaySeconds,
                onSelect = { seconds -> repository.update { it.copy(sendDelaySeconds = seconds) } },
            )
            Spacer(Modifier.height(12.dp))
            LinkRow(stringResource(R.string.scheduled_title), onClick = onShowScheduled)

            Spacer(Modifier.height(28.dp))
            SectionHeading(stringResource(R.string.settings_blocking))
            LinkRow(stringResource(R.string.blocked_title), onClick = onShowBlocked)

            Spacer(Modifier.height(28.dp))
            BackupSection()
        }
    }
}

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 10.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(top = 4.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A row of mutually exclusive choices, drawn like BifroSSH's settings rail: the chosen one
 * on a 15% wash of the accent with a 35% accent border and accent text.
 */
@Composable
private fun <T> Choices(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: (T) -> Boolean = { true },
) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val isEnabled = enabled(value)
            val accent = MaterialTheme.colorScheme.primary
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .heightIn(min = touchTarget(48.dp))
                    .clip(shape)
                    .background(if (isSelected) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        BorderStroke(1.dp, if (isSelected) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline),
                        shape,
                    )
                    .selectable(isSelected, enabled = isEnabled, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isSelected -> accent
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fade = if (enabled) 1f else 0.5f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = touchTarget(56.dp))
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade))
            if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Accessibility mode's switch, with its four parts under it, usable while it is on. */
@Composable
private fun AccessibilitySection(a11y: Accessibility, onChange: (Accessibility) -> Unit) {
    SectionHeading(stringResource(R.string.settings_accessibility))
    SwitchRow(
        stringResource(R.string.accessibility_mode),
        stringResource(R.string.accessibility_mode_hint),
        a11y.on,
        { onChange(a11y.copy(on = it)) },
    )
    val part = Modifier.padding(start = 16.dp)
    SwitchRow(
        stringResource(R.string.accessibility_contrast), stringResource(R.string.accessibility_contrast_hint),
        a11y.highContrast, { onChange(a11y.copy(highContrast = it)) }, part, enabled = a11y.on,
    )
    SwitchRow(
        stringResource(R.string.accessibility_text), stringResource(R.string.accessibility_text_hint),
        a11y.largerText, { onChange(a11y.copy(largerText = it)) }, part, enabled = a11y.on,
    )
    SwitchRow(
        stringResource(R.string.accessibility_targets), stringResource(R.string.accessibility_targets_hint),
        a11y.largerTargets, { onChange(a11y.copy(largerTargets = it)) }, part, enabled = a11y.on,
    )
    SwitchRow(
        stringResource(R.string.accessibility_motion), stringResource(R.string.accessibility_motion_hint),
        a11y.reducedMotion, { onChange(a11y.copy(reducedMotion = it)) }, part, enabled = a11y.on,
    )
}

@Composable
private fun RadioRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = touchTarget(52.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row that opens another page. */
@Composable
internal fun LinkRow(title: String, hint: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = touchTarget(52.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        if (hint != null) Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The theme's own accent, a row of swatches, and a field for any hex colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(accent: Int?, onAccent: (Int?) -> Unit) {
    FlowRow(
        Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val themeLabel = stringResource(R.string.settings_accent_theme)
        Swatch(
            colour = null,
            selected = accent == null,
            description = themeLabel,
            onClick = { onAccent(null) },
        )
        accentSwatches.forEach { swatch ->
            Swatch(
                colour = swatch,
                selected = accent == swatch,
                description = stringResource(R.string.settings_accent_custom, toHex(swatch)),
                onClick = { onAccent(swatch) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    var text by rememberSaveable(accent) { mutableStateOf(accent?.let(::toHex).orEmpty()) }
    val parsed = remember(text) { parseHex(text) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parseHex(it)?.let(onAccent)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_accent_hex)) },
        placeholder = { Text("#58a6ff") },
        singleLine = true,
        isError = text.isNotBlank() && parsed == null,
        supportingText = if (text.isNotBlank() && parsed == null) {
            { Text(stringResource(R.string.settings_accent_hex_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
    )
}

/** One accent choice: a filled circle, or for the theme's own accent a half-and-half circle. */
@Composable
private fun Swatch(colour: Int?, selected: Boolean, description: String, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .size(touchTarget(48.dp))
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outline, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        if (colour != null) {
            Box(Modifier.fillMaxSize().background(Color(0xFF000000 or colour.toLong())))
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxSize().background(MaterialTheme.colorScheme.primary))
                Box(Modifier.weight(1f).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}

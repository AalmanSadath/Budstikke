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
import androidx.compose.material3.MaterialTheme
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
import dev.aalman.budstikke.settings.SettingsRepository
import dev.aalman.budstikke.settings.ThemeMode
import dev.aalman.budstikke.settings.ThemeSource
import dev.aalman.budstikke.sms.Sim
import dev.aalman.budstikke.ui.theme.dynamicColourAvailable
import dev.aalman.budstikke.ui.theme.parseHex
import dev.aalman.budstikke.ui.theme.toHex

/** Accents offered as swatches: BifroSSH's three, then a spread around the wheel. */
private val accentSwatches = listOf(
    0x58A6FF, 0xA78BFA, 0x155397, 0x10B981, 0x84CC16,
    0xF59E0B, 0xEF4444, 0xEC4899, 0x06B6D4, 0x8B5CF6,
)

@Composable
fun SettingsScreen(sims: List<Sim>, onBack: () -> Unit) {
    val repository = SettingsRepository.get(LocalContext.current)
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
            if (!dynamicColourAvailable) Hint(stringResource(R.string.settings_theme_dynamic_unavailable))

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
                hint = stringResource(R.string.settings_true_black_hint),
                checked = settings.trueBlack,
                onCheckedChange = { on -> repository.update { it.copy(trueBlack = on) } },
            )

            Spacer(Modifier.height(16.dp))
            Label(stringResource(R.string.settings_accent))
            AccentPicker(
                accent = settings.accent,
                onAccent = { accent -> repository.update { it.copy(accent = accent) } },
            )

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
                Hint(stringResource(R.string.settings_sim_hint))
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
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
                    .heightIn(min = 48.dp)
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
private fun SwitchRow(title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
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
            .size(48.dp)
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

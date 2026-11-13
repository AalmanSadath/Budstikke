package dev.aalman.budstikke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.ui.theme.touchTarget

/**
 * The top row from BifroSSH's phone layout (narrow.css): 48 dp tall with a 1 dp border
 * under it, on the panel colour one step up from the page, title at 17 sp semibold. A back
 * arrow, when there is one, sits 4 dp in from the corner as BifroSSH's does.
 */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    /** Makes the leading content and title one tappable area, such as to open details. */
    onTitleClick: (() -> Unit)? = null,
    /** Buttons for the right of the bar; those that do not fit go into a menu. */
    items: List<BarItem> = emptyList(),
    /** Anything else for the right of the bar, after [items]. */
    actions: @Composable RowScope.() -> Unit = {},
) {
    val button = barButtonSize()
    // Measured in the style the title is drawn in, letter spacing included.
    val titleStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    val titleWidth = with(LocalDensity.current) { rememberTextMeasurer().measure(title, titleStyle, maxLines = 1).size.width.toDp() }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Room for the buttons is what is left once the title has what it needs, up to 60%
        // of the bar; with larger text the title needs more, and buttons that do not fit
        // move into a menu instead of squeezing the title down to a letter or two.
        val fixed = 8.dp + (if (onBack != null) button else 0.dp) + (if (leading != null) LEADING_WIDTH else 0.dp)
        // The title's padding, and a little over for rounding.
        val titleRoom = minOf(titleWidth + 20.dp, maxWidth * TITLE_SHARE)
        val (shown, menu) = splitBarItems(items, ((maxWidth - fixed - titleRoom) / button).toInt())
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = touchTarget(48.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) BarAction(R.drawable.ic_arrow_back, R.string.back, onBack)
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Spacer(Modifier.width(4.dp))
                    leading()
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            shown.forEach { BarAction(it.icon, it.label, it.onClick) }
            if (menu.isNotEmpty()) BarMenu(menu)
            actions()
        }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

/** A top bar button: its icon, its name (read by TalkBack, and shown when in the menu), and what it does. */
data class BarItem(
    val icon: Int,
    val label: Int,
    /** Always in the menu, never a button of its own. */
    val inMenu: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Which [items] get a button when there is room for [slots] buttons, and which go into the
 * menu. Buttons keep their order, so those at the end go first; the menu takes a slot.
 */
internal fun splitBarItems(items: List<BarItem>, slots: Int): Pair<List<BarItem>, List<BarItem>> {
    val buttons = items.filterNot { it.inMenu }
    val menuOnly = items.filter { it.inMenu }
    if (menuOnly.isEmpty() && buttons.size <= slots) return buttons to emptyList()
    val keep = (slots - 1).coerceIn(0, buttons.size)
    return buttons.take(keep) to buttons.drop(keep) + menuOnly
}

/** The widest a leading avatar or close button is taken to be. */
private val LEADING_WIDTH = 48.dp
private const val TITLE_SHARE = 0.6f

/** Bar icons grow with the text size, up to half again their size. */
private const val MAX_ICON_SCALE = 1.5f

@Composable
fun barIconSize(): Dp = 24.dp * LocalDensity.current.fontScale.coerceIn(1f, MAX_ICON_SCALE)

@Composable
fun barButtonSize(): Dp = maxOf(touchTarget(40.dp), barIconSize() + 16.dp)

@Composable
internal fun BarAction(icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(barButtonSize())) {
        Icon(painterResource(icon), stringResource(label), Modifier.size(barIconSize()), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** The ⋮ button and its menu of bar items that did not get a button of their own. */
@Composable
internal fun BarMenu(items: List<BarItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarAction(R.drawable.ic_more, R.string.more_options) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(item.label)) },
                    leadingIcon = { Icon(painterResource(item.icon), null) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

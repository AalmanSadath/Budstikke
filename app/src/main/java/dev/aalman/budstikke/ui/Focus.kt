package dev.aalman.budstikke.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Clears focus, taking the cursor out of a text field and closing the keyboard, when this
 * area is tapped. Only a tap counts: a drag that scrolls the area leaves focus alone. It
 * watches touches before the area's own children do and consumes nothing, so bubbles and
 * buttons in it still respond to the same tap.
 */
fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier = pointerInput(focusManager) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) {
                if (!moved) focusManager.clearFocus()
                break
            }
        }
    }
}

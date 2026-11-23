package com.kadri.stalkerplayerpro.core.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors

fun Modifier.tvFocusItem(
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedBorderColor: Color? = null,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedBackgroundColor: Color? = null,
    unfocusedBackgroundColor: Color = Color.Transparent,
    borderWidth: Dp = 2.dp,
    onFocusChangedCallback: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    var longPressHandled by remember { mutableStateOf(false) }
    var isKeyDownOnThisItem by remember { mutableStateOf(false) }
    var keyDownStartTime by remember { mutableStateOf(0L) }
    val interactionSource = remember { MutableInteractionSource() }
    val extraColors = LocalTvExtraColors.current

    val activeBorderColor = focusedBorderColor ?: extraColors.focusRing
    val activeBgColor = focusedBackgroundColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)

    val currentBorderColor = if (isFocused && enabled) activeBorderColor else unfocusedBorderColor
    val currentBgColor = if (isFocused && enabled) activeBgColor else unfocusedBackgroundColor

    val baseModifier = if (focusRequester != null) {
        this.focusRequester(focusRequester)
    } else {
        this
    }

    baseModifier
        .clip(shape)
        .background(currentBgColor, shape)
        .border(BorderStroke(borderWidth, currentBorderColor), shape)
        .onFocusChanged { focusState ->
            val nowFocused = focusState.isFocused
            if (isFocused != nowFocused) {
                isFocused = nowFocused
                if (!nowFocused) {
                    isKeyDownOnThisItem = false
                    longPressHandled = false
                }
                onFocusChangedCallback?.invoke(nowFocused)
            }
        }
        .focusable(enabled = enabled, interactionSource = interactionSource)
        .onPreviewKeyEvent { keyEvent ->
            if (!enabled) return@onPreviewKeyEvent false

            when (keyEvent.key) {
                Key.Menu -> {
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                        val action = onMenuClick ?: onLongClick
                        if (action != null) {
                            action.invoke()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }

                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            val repeat = keyEvent.nativeKeyEvent.repeatCount
                            if (repeat == 0) {
                                longPressHandled = false
                                isKeyDownOnThisItem = true
                                keyDownStartTime = System.currentTimeMillis()
                            } else if (!longPressHandled && onLongClick != null) {
                                val elapsed = System.currentTimeMillis() - keyDownStartTime
                                if (repeat >= 10 || (elapsed >= 700L && repeat >= 4)) {
                                    longPressHandled = true
                                    onLongClick.invoke()
                                }
                            }
                            true
                        }

                        KeyEventType.KeyUp -> {
                            if (isKeyDownOnThisItem && !longPressHandled) {
                                onClick()
                            }
                            isKeyDownOnThisItem = false
                            longPressHandled = false
                            true
                        }

                        else -> false
                    }
                }

                else -> false
            }
        }
        .pointerInput(enabled, onClick, onLongClick) {
            if (enabled) {
                detectTapGestures(
                    onLongPress = { onLongClick?.invoke() },
                    onTap = { onClick() }
                )
            }
        }
}

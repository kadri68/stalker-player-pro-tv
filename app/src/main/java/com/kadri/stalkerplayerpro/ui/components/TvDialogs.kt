package com.kadri.stalkerplayerpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvFocusTrap
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem

@Composable
fun TvModalDialog(
    title: String,
    onDismiss: () -> Unit,
    maxWidth: Dp = 560.dp,
    restoreFocusRequester: FocusRequester? = null,
    closeBtnRequester: FocusRequester = remember { FocusRequester() },
    scrollableBody: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(firstFocusRequester: FocusRequester) -> Unit
) {
    val extraColors = LocalTvExtraColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        TvFocusTrap(
            restoreFocusRequester = restoreFocusRequester,
            onDismiss = onDismiss
        ) { firstRequester ->
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = maxWidth)
                    .heightIn(max = 520.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(2.dp, extraColors.focusRing.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                                    runCatching { firstRequester.requestFocus() }.isSuccess
                                } else {
                                    false
                                }
                            }
                            .tvFocusItem(
                                focusRequester = closeBtnRequester,
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.30f),
                                onClick = onDismiss
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            LtrText(
                                text = "X",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val bodyModifier = if (scrollableBody) {
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                }

                Column(modifier = bodyModifier) {
                    content(firstRequester)
                }

                if (footer != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    footer()
                }
            }
        }
    }
}

data class TvMenuOption(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val isDestructive: Boolean = false
)

@Composable
fun TvActionMenuDialog(
    title: String,
    options: List<TvMenuOption>,
    restoreFocusRequester: FocusRequester? = null,
    onSelect: (TvMenuOption) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val menuListState = rememberLazyListState()
    val closeBtnRequester = remember { FocusRequester() }
    val optRequesters = remember(options.size) { mutableStateMapOf<Int, FocusRequester>() }

    TvModalDialog(
        title = title,
        onDismiss = onDismiss,
        maxWidth = 460.dp,
        restoreFocusRequester = restoreFocusRequester,
        closeBtnRequester = closeBtnRequester
    ) { firstRequester ->
        optRequesters[0] = firstRequester
        LazyColumn(
            state = menuListState,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = options,
                key = { idx, opt -> "menu_opt_${opt.id}_$idx" }
            ) { index, option ->
                val textColor = if (option.isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val itemReq = if (index == 0) firstRequester else optRequesters.getOrPut(index) { FocusRequester() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvVerticalStepItem(
                            index = index,
                            totalCount = options.size,
                            listState = menuListState,
                            requesters = optRequesters,
                            scope = scope,
                            onExitTop = { runCatching { closeBtnRequester.requestFocus() }.isSuccess }
                        )
                        .tvFocusItem(
                            focusRequester = itemReq,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            onClick = {
                                onDismiss()
                                onSelect(option)
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (option.icon != null) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TvPinDialog(
    title: String,
    isAr: Boolean,
    errorMessage: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    onPinSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val closeBtnRequester = remember { FocusRequester() }

    TvModalDialog(
        title = title,
        onDismiss = onDismiss,
        maxWidth = 380.dp,
        restoreFocusRequester = restoreFocusRequester,
        closeBtnRequester = closeBtnRequester,
        scrollableBody = true
    ) { firstRequester ->
        val padRequesters = remember {
            List(4) { r -> List(3) { c -> if (r == 0 && c == 0) firstRequester else FocusRequester() } }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val filled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                2.dp,
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        LtrText(
                            text = if (filled) "*" else "-",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "DEL")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEachIndexed { rIdx, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { cIdx, key ->
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 42.dp)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (ev.key) {
                                            Key.DirectionUp -> {
                                                if (rIdx > 0) runCatching { padRequesters[rIdx - 1][cIdx].requestFocus() }.isSuccess
                                                else runCatching { closeBtnRequester.requestFocus() }.isSuccess
                                            }
                                            Key.DirectionDown -> {
                                                if (rIdx < 3) runCatching { padRequesters[rIdx + 1][cIdx].requestFocus() }.isSuccess
                                                else true
                                            }
                                            Key.DirectionLeft -> {
                                                val targetC = if (isAr) cIdx + 1 else cIdx - 1
                                                if (targetC in 0..2) runCatching { padRequesters[rIdx][targetC].requestFocus() }.isSuccess
                                                else true
                                            }
                                            Key.DirectionRight -> {
                                                val targetC = if (isAr) cIdx - 1 else cIdx + 1
                                                if (targetC in 0..2) runCatching { padRequesters[rIdx][targetC].requestFocus() }.isSuccess
                                                else true
                                            }
                                            else -> false
                                        }
                                    }
                                    .tvFocusItem(
                                        focusRequester = padRequesters[rIdx][cIdx],
                                        shape = RoundedCornerShape(8.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                        onClick = {
                                            when (key) {
                                                "C" -> pin = ""
                                                "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                else -> {
                                                    if (pin.length < 4) {
                                                        val updated = pin + key
                                                        pin = updated
                                                        if (updated.length == 4) {
                                                            onPinSubmitted(updated)
                                                            pin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    LtrText(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAr) "الرمز الافتراضي: 0000" else "Default PIN: 0000",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TvTextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    restoreFocusRequester: FocusRequester? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    TvModalDialog(
        title = title,
        onDismiss = onDismiss,
        maxWidth = 480.dp,
        restoreFocusRequester = restoreFocusRequester,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (text.isNotBlank()) {
                                    onConfirm(text.trim())
                                    onDismiss()
                                }
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { _ ->
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

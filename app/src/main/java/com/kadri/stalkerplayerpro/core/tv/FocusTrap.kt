package com.kadri.stalkerplayerpro.core.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TvFocusTrap(
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    restoreFocusRequester: FocusRequester? = null,
    onDismiss: () -> Unit,
    content: @Composable (firstItemRequester: FocusRequester) -> Unit
) {
    BackHandler {
        restoreFocusRequester?.let { runCatching { it.requestFocus() } }
        onDismiss()
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { initialFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier.focusGroup()
    ) {
        content(initialFocusRequester)
    }
}

fun Modifier.blockFocusWhen(blocked: Boolean): Modifier =
    this.focusProperties {
        canFocus = !blocked
    }

@Composable
fun TvSafeSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = focusRequester ?: remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isEditing) {
        BackHandler {
            isEditing = false
            keyboardController?.hide()
            runCatching { buttonFocusRequester.requestFocus() }
        }

        LaunchedEffect(Unit) {
            delay(50)
            runCatching { textFieldFocusRequester.requestFocus() }
            keyboardController?.show()
        }

        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(textFieldFocusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        isEditing = false
                        keyboardController?.hide()
                        runCatching { buttonFocusRequester.requestFocus() }
                    },
                    onDone = {
                        isEditing = false
                        keyboardController?.hide()
                        runCatching { buttonFocusRequester.requestFocus() }
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                onQueryChange("")
                                isEditing = false
                                keyboardController?.hide()
                                runCatching { buttonFocusRequester.requestFocus() }
                            }
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .tvFocusItem(
                    focusRequester = buttonFocusRequester,
                    shape = RoundedCornerShape(10.dp),
                    unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    onClick = { isEditing = true }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = query.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

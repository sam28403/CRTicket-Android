package cc.samlab.ticket.ui.railfan.historical

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoricalTrainControls(
    train: String,
    onTrainChange: (String) -> Unit,
    version: String,
    versions: List<String>,
    onVersionChange: (String) -> Unit,
    loading: Boolean,
    onQuery: () -> Unit,
) {
    val text = rememberAppText()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(false) }
    val canQuery = train.isNotBlank() && version in versions && !loading
    val submit = {
        if (canQuery) {
            focus.clearFocus()
            keyboard?.hide()
            onQuery()
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = train,
                onValueChange = { onTrainChange(historicalTrainInput(it)) },
                label = { Text(text(R.string.ticket_train)) },
                placeholder = { Text(text(R.string.train_query_train_hint)) },
                singleLine = true,
                enabled = !loading,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).testTag("historical-train-code"),
            )
            val datesEnabled = !loading && versions.isNotEmpty()
            ExposedDropdownMenuBox(
                expanded = expanded && datesEnabled,
                onExpandedChange = {
                    focus.clearFocus()
                    keyboard?.hide()
                    expanded = it
                },
                modifier = Modifier.weight(1.4f),
            ) {
                OutlinedTextField(
                    value = version,
                    onValueChange = {},
                    label = { Text(text(R.string.historical_train_date)) },
                    readOnly = true,
                    singleLine = true,
                    enabled = datesEnabled,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && datesEnabled) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = datesEnabled)
                        .testTag("historical-train-date"),
                )
                if (expanded && datesEnabled) Popup(
                    popupPositionProvider = HistoricalDateMenuPosition,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        modifier = Modifier.exposedDropdownSize(),
                        shape = MenuDefaults.shape,
                        color = MenuDefaults.containerColor,
                        tonalElevation = MenuDefaults.TonalElevation,
                        shadowElevation = MenuDefaults.ShadowElevation,
                    ) {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 280.dp)
                                .testTag("historical-train-dates"),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(versions, key = { it }) { date ->
                                DropdownMenuItem(
                                    text = { Text(date) },
                                    onClick = {
                                        onVersionChange(date)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = submit,
            enabled = canQuery,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("historical-train-submit"),
        ) { Text(text(R.string.action_query)) }
    }
}

private object HistoricalDateMenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val y = if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) anchorBounds.bottom
            else anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

internal fun historicalTrainInput(value: String): String = buildString {
    var hasLetter = false
    for (character in value) {
        if (length == 5) break
        val upper = if (character in 'a'..'z') character.uppercaseChar() else character
        when {
            upper in '0'..'9' -> append(upper)
            upper in 'A'..'Z' && !hasLetter -> {
                append(upper)
                hasLetter = true
            }
        }
    }
}

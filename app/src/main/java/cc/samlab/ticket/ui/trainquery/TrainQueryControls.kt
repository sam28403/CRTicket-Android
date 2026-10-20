package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import org.json.JSONObject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrainQueryControls(
    controller: NativeTrainQueryController,
    state: JSONObject,
    current: Boolean,
    onDate: () -> Unit,
) {
    val textResources = rememberAppText()
    val loading = state.optBoolean("loading")
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }
    fun submit() {
        dismissKeyboard()
        controller.dispatch("query")
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = if (current) controller.train else state.optString("train"),
                onValueChange = { controller.edit(code = it) },
                label = { Text(textResources(R.string.ticket_train)) },
                placeholder = { Text(textResources(R.string.train_query_train_hint)) },
                singleLine = true,
                enabled = !loading,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).testTag("train-query-code"),
            )
            Box(Modifier.weight(1.4f)) {
                OutlinedTextField(
                    value = if (current) controller.date else state.optString("date"),
                    onValueChange = {},
                    label = { Text(textResources(R.string.train_query_start_date)) },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable(enabled = !loading, role = Role.Button) {
                    dismissKeyboard()
                    onDate()
                }.testTag("train-query-date"))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { submit() },
                enabled = !loading,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(48.dp).testTag("train-query-submit"),
            ) { Text(textResources(R.string.action_query)) }
            FilledTonalButton(
                onClick = {
                    dismissKeyboard()
                    controller.dispatch("source")
                },
                enabled = !loading,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(48.dp),
            ) {
                Text(textResources(
                    if (state.optBoolean("offline")) R.string.train_query_switch_online
                    else R.string.train_query_switch_offline,
                ))
            }
        }
    }
}

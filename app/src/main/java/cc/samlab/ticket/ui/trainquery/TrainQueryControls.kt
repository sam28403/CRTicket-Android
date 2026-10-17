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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrainQueryControls(controller: NativeTrainQueryController, onDate: () -> Unit) {
    val loading = controller.state.optBoolean("loading")
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
                value = controller.train,
                onValueChange = { controller.edit(code = it) },
                label = { Text("车次") },
                placeholder = { Text("例如 G1") },
                singleLine = true,
                enabled = !loading,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).testTag("train-query-code"),
            )
            Box(Modifier.weight(1.4f)) {
                OutlinedTextField(
                    value = controller.date,
                    onValueChange = {},
                    label = { Text("始发日期") },
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
            ) { Text("查询") }
            FilledTonalButton(
                onClick = {
                    dismissKeyboard()
                    controller.dispatch("source")
                },
                enabled = !loading,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(48.dp),
            ) { Text(if (controller.state.optBoolean("offline")) "切换为在线查询" else "切换为路路通离线") }
        }
    }
}

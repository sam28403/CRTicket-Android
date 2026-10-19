package cc.samlab.ticket.ui.notice

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cc.samlab.ticket.R

internal const val IMPORTANT_NOTICE_SUPPRESSED = "importantNoticeSuppressed"

/** One confirmation per Activity session; only an explicit opt-out survives a new launch. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun ImportantNoticeDialog() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("android", Context.MODE_PRIVATE)
    }
    var confirmed by rememberSaveable {
        mutableStateOf(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
    }
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }
    if (confirmed) return

    val contentHeight = minOf(360, LocalConfiguration.current.screenHeightDp / 3).dp
    AlertDialog(
        modifier = Modifier.testTag("important-notice"),
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.important_notice_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.important_notice_message),
                    modifier = Modifier
                        .heightIn(max = contentHeight)
                        .verticalScroll(rememberScrollState()),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("important-notice-opt-out")
                        .toggleable(
                            value = doNotShowAgain,
                            role = Role.Checkbox,
                            onValueChange = { doNotShowAgain = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = doNotShowAgain, onCheckedChange = null)
                    Text(stringResource(R.string.important_notice_do_not_show))
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("important-notice-confirm"),
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    if (doNotShowAgain) {
                        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).apply()
                    }
                    confirmed = true
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
    )
}

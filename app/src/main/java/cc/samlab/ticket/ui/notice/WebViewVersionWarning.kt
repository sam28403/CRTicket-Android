package cc.samlab.ticket.ui.notice

import android.webkit.WebView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.web.WebViewVersionPolicy

@Composable
internal fun WebViewVersionWarning() {
    val version = remember {
        runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed || !WebViewVersionPolicy.isUnsupported(version)) return

    AlertDialog(
        modifier = Modifier.testTag("webview-version-warning"),
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.webview_version_warning_title)) },
        text = {
            Text(stringResource(
                R.string.webview_version_warning_message,
                version.orEmpty(),
                WebViewVersionPolicy.MINIMUM_MAJOR,
            ))
        },
        confirmButton = {
            TextButton(onClick = { dismissed = true }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
    )
}

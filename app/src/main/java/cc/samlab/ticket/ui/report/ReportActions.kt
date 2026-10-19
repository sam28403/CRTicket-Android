package cc.samlab.ticket.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun ReportActions(report: IssueReport, onExternal: (String) -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.report_preview), style = MaterialTheme.typography.titleMedium)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                report.body(),
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = report.ready,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (report.needsClipboard) {
                    copyReport(context, report, R.string.report_long_copied)
                }
                onExternal(report.url(includeBody = !report.needsClipboard))
            },
        ) { Text(stringResource(R.string.report_submit)) }
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { copyReport(context, report, R.string.report_copied) },
        ) { Text(stringResource(R.string.report_copy)) }
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onExternal(ISSUES_URL) },
        ) { Text(stringResource(R.string.report_browse)) }
    }
}

private fun copyReport(context: Context, report: IssueReport, message: Int) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(report.title, report.body()))
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

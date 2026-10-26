package cc.samlab.ticket.ui.quiz

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun QuizReportForm(report: QuizIssueReport, onReason: (String) -> Unit, onExternal: (String) -> Unit) {
    val context = LocalContext.current
    fun copy(message: Int) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(report.title, report.body()))
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(24.dp).testTag("quiz-report-fields"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QuizReportField(R.string.quiz_report_question, report.question)
                QuizReportField(R.string.quiz_report_reference, report.reference)
                QuizReportField(R.string.quiz_report_answer, report.answer)
                QuizReportField(R.string.quiz_report_time, report.answeredAt)
                OutlinedTextField(
                    value = report.reason,
                    onValueChange = onReason,
                    label = { Text(stringResource(R.string.quiz_report_reason)) },
                    modifier = Modifier.fillMaxWidth().testTag("quiz-report-reason"),
                    minLines = 3,
                    shape = RoundedCornerShape(20.dp),
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = report.ready,
                    onClick = {
                        if (report.needsClipboard) copy(R.string.report_long_copied)
                        onExternal(report.url(includeBody = !report.needsClipboard))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quiz-report-open"),
                ) { Text(stringResource(R.string.quiz_report_open)) }
                FilledTonalButton(
                    onClick = { copy(R.string.report_copied) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quiz-report-copy"),
                ) { Text(stringResource(R.string.quiz_report_copy)) }
            }
        }
    }
}

@Composable
private fun QuizReportField(label: Int, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

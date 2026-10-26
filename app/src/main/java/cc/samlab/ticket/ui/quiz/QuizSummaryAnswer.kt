package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun QuizSummaryAnswer(
    question: QuizQuestion,
    index: Int,
    onReport: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().testTag("quiz-summary-answer-$index"),
        color = colors.surfaceContainerLowest,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.quiz_summary_question, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Text(question.statement.render(), style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalIconButton(
                onClick = { onReport(index) },
                modifier = Modifier.testTag("quiz-report-$index"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) { Icon(Icons.Outlined.ReportProblem, stringResource(R.string.quiz_report_error)) }
        }
    }
}

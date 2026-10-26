package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.projectTitleFont

@Composable
internal fun QuizSummary(questions: List<QuizQuestion>, answers: List<Int>, onReport: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val correct = questions.indices.count { answers[it] == questions[it].correctIndex }
    val incorrect = questions.indices.filter {
        answers[it] >= 0 && answers[it] != questions[it].correctIndex
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("quiz-summary"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.quiz_summary_result, correct, questions.size),
                modifier = Modifier.padding(bottom = 16.dp).testTag("quiz-summary-result"),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = projectTitleFont,
            )
        }
        items(incorrect, key = { it }) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("quiz-summary-answer-$index"),
                color = colors.surfaceContainerLowest,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.quiz_summary_question, index + 1),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            questions[index].statement.render(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onReport(index) },
                        modifier = Modifier.testTag("quiz-report-$index"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Outlined.ReportProblem, stringResource(R.string.quiz_report_error))
                    }
                }
            }
        }
    }
}

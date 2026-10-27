package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R

@Composable
internal fun QuizScoreCard(
    questions: List<QuizQuestion>,
    answers: List<Int>,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    profile: QuizShareProfile? = null,
) {
    val results = questions.indices.map { answers[it] == questions[it].correctIndex }
    val correct = results.count { it }
    val perfect = correct == questions.size
    val colors = MaterialTheme.colorScheme
    Surface(modifier.testTag("quiz-score-card"), shape = RoundedCornerShape(32.dp), color = colors.primaryContainer) {
        val large = expanded
        val ringSize = if (large) 176.dp else 128.dp
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                16.dp, Alignment.Top,
            ),
        ) {
            QuizScoreHeading(perfect, profile)
            Box(
                Modifier.size(ringSize),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { correct.toFloat() / questions.size },
                    modifier = Modifier.fillMaxSize(),
                    color = colors.primary,
                    trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${correct * QUIZ_POINTS_PER_ANSWER}",
                        modifier = Modifier.testTag("quiz-score"),
                        fontSize = if (large) 56.sp else 44.sp,
                        fontWeight = FontWeight.Bold, lineHeight = if (large) 64.sp else 52.sp,
                    )
                    Text("/ ${questions.size * QUIZ_POINTS_PER_ANSWER}", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                stringResource(R.string.quiz_summary_result, correct, questions.size),
                Modifier.testTag("quiz-summary-result"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            QuizResultGrid(results, columns = if (large) 5 else 10)
        }
    }
}

@Composable
private fun QuizResultGrid(results: List<Boolean>, columns: Int) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.testTag("quiz-result-grid"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        results.chunked(columns).forEachIndexed { row, values ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEachIndexed { column, correct ->
                    Surface(
                        Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        color = if (correct) colors.primary else colors.errorContainer,
                        contentColor = if (correct) colors.onPrimary else colors.onErrorContainer,
                    ) {
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text("${row * columns + column + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

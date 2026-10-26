package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R

@Composable
internal fun QuizScoreCard(
    questions: List<QuizQuestion>,
    answers: List<Int>,
    modifier: Modifier = Modifier,
    expanded: Boolean,
) {
    val results = questions.indices.map { answers[it] == questions[it].correctIndex }
    val correct = results.count { it }
    val perfect = correct == questions.size
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(32.dp), color = colors.primaryContainer) {
        BoxWithConstraints {
            val large = expanded && (maxHeight == Dp.Infinity || maxHeight >= 620.dp)
            val compact = maxHeight < 560.dp
            val ringSize = if (large) 208.dp else if (maxHeight < 500.dp) 120.dp else 144.dp
            val minimumHeight = if (maxHeight == Dp.Infinity) minHeight else maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .heightIn(min = minimumHeight).padding(if (compact) 20.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    if (large) 24.dp else if (compact) 12.dp else 16.dp, Alignment.CenterVertically,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = colors.primary) {
                        Icon(
                            if (perfect) Icons.Outlined.EmojiEvents else Icons.Outlined.DoneAll,
                            null, Modifier.padding(12.dp).size(24.dp), tint = colors.onPrimary,
                        )
                    }
                    Text(
                        stringResource(if (perfect) R.string.quiz_summary_perfect else R.string.quiz_summary_heading),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
                            "$correct", fontSize = if (large) 64.sp else 48.sp,
                            fontWeight = FontWeight.Bold, lineHeight = if (large) 72.sp else 56.sp,
                        )
                        Text("/ ${questions.size}", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    stringResource(R.string.quiz_summary_result, correct, questions.size),
                    Modifier.testTag("quiz-summary-result"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                QuizResultGrid(results, columns = if (compact) 10 else 5)
            }
        }
    }
}

@Composable
private fun QuizResultGrid(results: List<Boolean>, columns: Int) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun QuizSummary(questions: List<QuizQuestion>, answers: List<Int>, onReport: (Int) -> Unit) {
    val incorrect = questions.indices.filter { answers[it] != questions[it].correctIndex }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("quiz-summary")) {
        val viewportHeight = maxHeight
        if (maxWidth >= 700.dp && incorrect.isNotEmpty()) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                QuizScoreCard(questions, answers, Modifier.weight(1f).fillMaxHeight(), expanded = true)
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    item { SummaryAnswersHeading() }
                    items(incorrect, key = { it }) { index ->
                        QuizSummaryAnswer(questions[index], index, onReport)
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    QuizScoreCard(
                        questions, answers,
                        Modifier.fillMaxWidth().height(
                            if (incorrect.isEmpty()) (viewportHeight - 24.dp).coerceAtLeast(480.dp)
                            else (viewportHeight * 0.52f).coerceAtLeast(520.dp),
                        ),
                        expanded = incorrect.isEmpty(),
                    )
                }
                if (incorrect.isNotEmpty()) {
                    item { SummaryAnswersHeading() }
                    items(incorrect, key = { it }) { index ->
                        QuizSummaryAnswer(
                            questions[index], index, onReport,
                            Modifier.heightIn(min = if (incorrect.size == 1) viewportHeight * 0.35f else 144.dp),
                        )
                    }
                }
            }
        }
        if (incorrect.isEmpty()) QuizCelebration()
    }
}

@Composable
private fun SummaryAnswersHeading() {
    Text(
        stringResource(R.string.quiz_summary_answers),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

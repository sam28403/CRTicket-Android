package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.projectTitleFont

@Composable
internal fun QuizQuestionContent(
    question: QuizQuestion,
    page: Int,
    total: Int,
    answer: Int,
    wrongFeedback: Boolean,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("quiz-question-$page"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.quiz_question_number, page + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            question.title.render(),
            modifier = Modifier.padding(bottom = 12.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = projectTitleFont,
        )
        question.options.forEachIndexed { index, option ->
            QuizOption(option, index, answer, question.correctIndex, wrongFeedback, enabled) {
                onSelect(index)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

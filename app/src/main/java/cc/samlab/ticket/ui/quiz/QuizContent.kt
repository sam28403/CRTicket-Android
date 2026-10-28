package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.StepPageTransition

@Composable
internal fun QuizContent(
    session: QuizSession,
    busy: Boolean,
    wrongFeedback: String?,
    modifier: Modifier = Modifier,
    onAnswer: (Int, Int) -> Unit,
    onMove: (Int) -> Unit,
    onDone: () -> Unit,
    onReport: (Int) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = if (session.completed) 1120.dp else 720.dp).fillMaxWidth().weight(1f)) {
            if (!session.completed) Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                QuizProgress(session.current, session.answers)
            }
            // A replacement is a new presentation, not another page of the old set.
            key(session.date, session.questions) {
                StepPageTransition(
                    page = session.current,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "quiz-question",
                ) { page ->
                    if (page == session.questions.size) {
                        QuizSummary(session.questions, session.answers, onReport, busy, onDone, onReset)
                    } else {
                        val question = session.questions[page]
                        QuizQuestionContent(
                            question, page, session.questions.size, session.answers[page],
                            wrongFeedback == question.id, !busy && !session.completed,
                        ) { choice -> onAnswer(page, choice) }
                    }
                }
            }
            if (!session.completed) Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { if (!busy) onMove(-1) },
                    enabled = session.current > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("quiz-previous"),
                ) { Text(stringResource(R.string.quiz_previous)) }
                Button(
                    onClick = { if (!busy) onMove(1) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("quiz-next"),
                ) {
                    Text(stringResource(
                        if (session.current == session.questions.lastIndex) R.string.quiz_finish
                        else R.string.quiz_next,
                    ))
                }
            }
        }
    }
}

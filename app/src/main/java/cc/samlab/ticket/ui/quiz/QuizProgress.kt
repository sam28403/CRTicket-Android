package cc.samlab.ticket.ui.quiz

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun QuizProgress(current: Int, answers: List<Int>) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(
                (current + 1).coerceAtMost(answers.size).toFloat(),
                0f..answers.size.toFloat(), answers.size - 1,
            )
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        answers.forEachIndexed { index, answer ->
            val color by animateColorAsState(
                when {
                    index == current -> colors.primary
                    answer >= 0 -> colors.secondary
                    else -> colors.outlineVariant
                },
                label = "quiz-progress-$index",
            )
            Box(
                Modifier.weight(1f).height(6.dp)
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
}

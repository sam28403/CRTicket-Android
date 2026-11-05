package cc.samlab.ticket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

internal val TrainCodeStrikeWidth = SemanticsPropertyKey<Float>("TrainCodeStrikeWidth")

internal data class TrainCodeStrikeThrough(
    val modifier: Modifier,
    val onTextLayout: (TextLayoutResult) -> Unit,
)

@Composable
internal fun rememberTrainCodeStrikeThrough(enabled: Boolean, color: Color): TrainCodeStrikeThrough {
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    return TrainCodeStrikeThrough(
        Modifier.semantics { this[TrainCodeStrikeWidth] = if (enabled) 2f else 0f }
            .drawWithContent {
                drawContent()
                val text = layout.value
                if (enabled && text != null) {
                    for (line in 0 until text.lineCount) {
                        val y = (text.getLineTop(line) + text.getLineBottom(line)) / 2f
                        drawLine(
                            color,
                            Offset(text.getLineLeft(line), y),
                            Offset(text.getLineRight(line), y),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
            },
        onTextLayout = { layout.value = it },
    )
}

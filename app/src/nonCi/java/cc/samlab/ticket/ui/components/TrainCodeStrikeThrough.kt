package cc.samlab.ticket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult

internal data class TrainCodeStrikeThrough(
    val modifier: Modifier,
    val onTextLayout: (TextLayoutResult) -> Unit,
)

@Composable
internal fun rememberTrainCodeStrikeThrough(enabled: Boolean, color: Color): TrainCodeStrikeThrough =
    TrainCodeStrikeThrough(Modifier) {}

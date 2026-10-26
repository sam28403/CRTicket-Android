package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.station.StationDiscoveryCard
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.TrainListRow
import org.json.JSONObject

@Composable
internal fun QuizOption(
    choice: QuizChoice,
    index: Int,
    answer: Int,
    correct: Int,
    wrongFeedback: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val revealed = answer >= 0
    val selected = answer == index
    val shake = remember { Animatable(0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(wrongFeedback, selected) {
        if (wrongFeedback && selected && index != correct) {
            repeat(4) { turn ->
                shake.animateTo(if (turn % 2 == 0) -8f else 8f, tween(55))
            }
            shake.animateTo(0f, tween(70))
        } else shake.snapTo(0f)
    }
    val highlighted = revealed && (index == correct || selected)
    val dark = colors.surface.luminance() < 0.5f
    val background = when {
        revealed && index == correct -> if (dark) Color(0xFF164B2B) else Color(0xFFB8F2C8)
        selected -> if (dark) Color(0xFF7C2020) else Color(0xFFFFDAD6)
        else -> colors.surfaceContainerLowest
    }
    val foreground = when {
        revealed && index == correct -> if (dark) Color(0xFFB8F2C8) else Color(0xFF07391B)
        selected -> if (dark) Color(0xFFFFDAD6) else Color(0xFF690005)
        else -> colors.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("quiz-option-$index")
            .semantics { this.selected = selected }
            .graphicsLayer { translationX = shake.value * density },
        shape = RoundedCornerShape(26.dp),
        color = background,
        border = BorderStroke(2.dp, background),
    ) {
        MaterialTheme(
            colorScheme = colors.copy(
                primaryContainer = background,
                onPrimaryContainer = foreground,
            ),
        ) {
            Column(Modifier.padding(2.dp)) {
                when (choice.kind) {
                    QuizOptionKind.STATION -> {
                        val row = remember(choice.label) { JSONObject().put("station", choice.label) }
                        StationDiscoveryCard(
                            row = row, compact = true, highlighted = highlighted,
                            enabled = enabled && !revealed, onClick = onSelect,
                        )
                    }
                    QuizOptionKind.TRAIN -> {
                        val row = remember(choice) {
                            TrainListRow(
                                choice.label, choice.from, choice.to, "", "", "", "", 0,
                                null, null, null,
                            )
                        }
                        TrainListItem(
                            row = row, highlighted = highlighted, enabled = enabled && !revealed,
                            showStationCount = false, showDetails = choice.from.isNotBlank(),
                            showMetrics = false, onClick = onSelect,
                        )
                    }
                    QuizOptionKind.TEXT -> Surface(
                        onClick = onSelect,
                        enabled = enabled && !revealed,
                        shape = RoundedCornerShape(24.dp),
                        color = background,
                    ) {
                        Text(
                            choice.label, Modifier.fillMaxWidth().padding(20.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = foreground,
                        )
                    }
                }
            }
        }
    }
}

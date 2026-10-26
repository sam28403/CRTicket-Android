package cc.samlab.ticket.ui.quiz

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

@Composable
internal fun QuizCelebration() {
    var finished by rememberSaveable { mutableStateOf(false) }
    val enabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val palette = with(MaterialTheme.colorScheme) {
        listOf(primary.toArgb(), tertiary.toArgb(), 0xFFFFC857.toInt(), 0xFFEF6F6C.toInt(), 0xFF76C7C0.toInt())
    }
    val parties = remember(palette) {
        listOf(0.0 to 305, 1.0 to 235).map { (x, angle) ->
            Party(
                speed = 12f, maxSpeed = 32f, damping = 0.92f,
                angle = angle, spread = 65, colors = palette,
                timeToLive = 2400L,
                emitter = Emitter(duration = 900, TimeUnit.MILLISECONDS).perSecond(90),
                position = Position.Relative(x, 0.8),
            )
        }
    }
    LaunchedEffect(Unit) {
        delay(4000)
        finished = true
    }
    if (enabled && !finished) KonfettiView(
        modifier = Modifier.fillMaxSize().testTag("quiz-celebration"),
        parties = parties,
    )
}

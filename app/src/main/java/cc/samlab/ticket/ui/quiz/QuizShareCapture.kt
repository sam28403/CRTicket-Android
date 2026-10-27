package cc.samlab.ticket.ui.quiz

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Records a separate score card without drawing it over the on-screen summary. */
@Composable
internal fun QuizShareCapture(session: QuizSession, profile: QuizShareProfile, onFinished: () -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.quiz_share)
    val layer = rememberGraphicsLayer()
    val drawn = remember { CompletableDeferred<Unit>() }
    val width = with(LocalDensity.current) { 400.dp.roundToPx() }
    Layout(
        modifier = Modifier.clearAndSetSemantics {},
        content = {
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawn.complete(Unit)
                }.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            ) {
                QuizScoreCard(
                    session.questions, session.answers,
                    expanded = session.answers.indices.all { session.answers[it] == session.questions[it].correctIndex },
                    profile = profile,
                )
            }
        },
    ) { measurables, _ ->
        val card = measurables.single().measure(Constraints(minWidth = width, maxWidth = width))
        layout(0, 0) { card.place(0, 0) }
    }
    LaunchedEffect(session, profile) {
        try {
            withTimeout(10_000) { drawn.await() }
            val bitmap = layer.toImageBitmap().asAndroidBitmap()
            val intent = quizShareIntent(context, session, bitmap)
            context.startActivity(Intent.createChooser(intent, shareTitle))
        } catch (_: TimeoutCancellationException) {
            Toast.makeText(context, R.string.quiz_share_failed, Toast.LENGTH_SHORT).show()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Toast.makeText(context, R.string.quiz_share_failed, Toast.LENGTH_SHORT).show()
        } finally {
            onFinished()
        }
    }
}

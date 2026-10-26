package cc.samlab.ticket.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

@Composable
internal fun StepPageTransition(
    page: Int,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable (Int) -> Unit,
) {
    AnimatedContent(
        targetState = page,
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            val direction = if (targetState > initialState) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else AnimatedContentTransitionScope.SlideDirection.Right
            (slideIntoContainer(direction, tween(280)) + fadeIn(tween(220)))
                .togetherWith(slideOutOfContainer(direction, tween(280)) + fadeOut(tween(180)))
        },
        label = label,
    ) { target -> content(target) }
}

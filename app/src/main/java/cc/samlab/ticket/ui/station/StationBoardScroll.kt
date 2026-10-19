package cc.samlab.ticket.ui.station

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyLayoutScrollScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/** Keep long station boards as quick to locate as short ones, without teleporting. */
internal suspend fun LazyGridState.animateToCurrentTrain(index: Int) {
    snapshotFlow { layoutInfo.totalItemsCount }.first { it > index }
    scroll {
        val layout = LazyLayoutScrollScope(this@animateToCurrentTrain, this)
        var previous = 0f
        animate(0f, 1f, animationSpec = tween(380, easing = FastOutSlowInEasing)) { progress, _ ->
            // Re-estimate the remaining distance as rows with different heights are measured.
            val fraction = if (previous < 1f) (progress - previous) / (1f - previous) else 0f
            layout.scrollBy(layout.calculateDistanceTo(index) * fraction)
            previous = progress
        }
        layout.snapToItem(index)
    }
}

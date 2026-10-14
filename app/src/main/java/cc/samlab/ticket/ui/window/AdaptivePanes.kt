package cc.samlab.ticket.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.window.layout.FoldingFeature

internal val LocalFoldingFeatures = staticCompositionLocalOf<List<FoldingFeature>> { emptyList() }
internal val LocalSinglePane = staticCompositionLocalOf { false }

/** Uses the actual available window, including split-screen and IME resizing. */
@Composable
internal fun AdaptivePanes(
    modifier: Modifier = Modifier,
    single: @Composable () -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    firstPaneFraction: Float = 0.5f,
    splitAtVerticalFold: Boolean = false,
) {
    val folds = LocalFoldingFeatures.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val singlePane = LocalSinglePane.current
    BoxWithConstraints(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        val fold = folds.firstOrNull {
            it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL ||
                (splitAtVerticalFold && it.orientation == FoldingFeature.Orientation.VERTICAL)
        }
        val bounds = fold?.bounds?.let {
            PaneBounds(
                it.left - origin.x.toInt(), it.top - origin.y.toInt(),
                it.right - origin.x.toInt(), it.bottom - origin.y.toInt(),
            )
        }
        val plan = if (singlePane) PanePlan(PaneBounds(0, 0, constraints.maxWidth, constraints.maxHeight)) else panePlan(
            constraints.maxWidth, constraints.maxHeight, density.density, bounds,
            fold?.orientation != FoldingFeature.Orientation.HORIZONTAL,
            firstPaneFraction,
        )
        if (plan.second == null) {
            Box(Modifier.fillMaxSize().testTag("adaptive-single")) { single() }
        } else {
            Layout(
                modifier = Modifier.fillMaxSize().testTag("adaptive-dual"),
                content = {
                    Box(Modifier.testTag("adaptive-first")) { first() }
                    Box(Modifier.testTag("adaptive-second")) { second() }
                },
            ) { measurables, _ ->
                val areas = listOf(plan.first, checkNotNull(plan.second))
                val panes = measurables.zip(areas).map { (child, area) ->
                    child.measure(Constraints.fixed(area.width, area.height))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    panes.zip(areas).forEach { (pane, area) -> pane.place(area.left, area.top) }
                }
            }
        }
    }
}

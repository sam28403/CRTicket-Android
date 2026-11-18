package cc.samlab.ticket.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ScrollFieldColors
import androidx.compose.material3.ScrollFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val WheelPageCount = 100_000
private const val MaxFlingPages = 12

@Stable
internal class PickerWheelState(val pager: PagerState, val itemCount: Int) {
    val selectedOption: Int get() = pager.currentPage % itemCount
    val isScrollInProgress: Boolean get() = pager.isScrollInProgress
}

@Composable
internal fun rememberPickerWheelState(itemCount: Int, initialOption: Int): PickerWheelState {
    val start = WheelPageCount / 2
    val pager = rememberPagerState(start - start % itemCount + initialOption) { WheelPageCount }
    return remember(pager, itemCount) { PickerWheelState(pager, itemCount) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PickerWheel(
    state: PickerWheelState,
    description: String,
    optionDescription: (Int) -> String,
    modifier: Modifier = Modifier,
    colors: ScrollFieldColors = ScrollFieldDefaults.colors(),
    field: @Composable (index: Int, selected: Boolean, enabled: Boolean) -> Unit = { index, selected, enabled ->
        ScrollFieldDefaults.Item(index = index, selected = selected, enabled = enabled, colors = colors)
    },
) {
    val pager = state.pager
    val enabled = state.itemCount > 1
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val shape = ScrollFieldDefaults.shape
    val snapDistance = remember { PagerSnapDistance.atMost(MaxFlingPages) }
    val snapAnimation = remember { spring<Float>(stiffness = Spring.StiffnessMediumLow) }
    LaunchedEffect(pager, haptics, enabled) {
        if (enabled) {
            var previousPage = pager.currentPage
            snapshotFlow { pager.currentPage }.collect { page ->
                val crossed = abs(page - previousPage)
                previousPage = page
                // A fast frame can pass multiple marks. Accessibility jumps can skip whole sections.
                val ticks = if (crossed > MaxFlingPages) 1 else crossed
                repeat(ticks) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
        }
    }
    VerticalPager(
        state = pager,
        userScrollEnabled = enabled,
        pageSize = PageSize.Fixed(ScrollFieldDefaults.ScrollFieldHeight / 3),
        snapPosition = SnapPosition.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pager,
            pagerSnapDistance = snapDistance,
            snapAnimationSpec = snapAnimation,
        ),
        modifier = modifier
            .indication(interactions, ripple(focusRingShape = shape))
            .background(if (enabled) colors.containerColor else colors.disabledContainerColor, shape)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionDown -> 1
                    Key.DirectionUp -> -1
                    else -> return@onKeyEvent false
                }
                val target = (pager.targetPage + direction).coerceIn(0, WheelPageCount - 1)
                scope.launch { pager.animateScrollToPage(target) }
                true
            }
            .focusable(enabled = enabled, interactionSource = interactions)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = optionDescription(pager.targetPage % state.itemCount)
                if (!enabled) {
                    disabled()
                } else {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        pager.targetPage.toFloat(), 0f..(WheelPageCount - 1).toFloat(), WheelPageCount - 2,
                    )
                    setProgress { position ->
                        val target = position.roundToInt().coerceIn(0, WheelPageCount - 1)
                        if (target == pager.targetPage) return@setProgress false
                        scope.launch { pager.animateScrollToPage(target) }
                        true
                    }
                }
            },
    ) { page ->
        Box(
            modifier = Modifier.fillMaxHeight()
                .focusProperties { canFocus = false }
                .clickable(enabled = enabled) { scope.launch { pager.animateScrollToPage(page) } },
            contentAlignment = Alignment.Center,
        ) {
            field(page % state.itemCount, page == pager.currentPage, enabled)
        }
    }
}

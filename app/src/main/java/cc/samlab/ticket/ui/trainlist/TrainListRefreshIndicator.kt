package cc.samlab.ticket.ui.trainlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Read's article refresh motion, adapted to the official pull-to-refresh state. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.TrainListRefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val offset = remember { Animatable(0f) }
    val alpha = remember { Animatable(if (refreshing) 1f else 0f) }
    val scale = remember { Animatable(if (refreshing) 1f else 0f) }
    var indeterminate by remember { mutableStateOf(refreshing) }
    val haptics = LocalHapticFeedback.current
    val bounce = remember {
        spring<Float>(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        )
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            indeterminate = true
            alpha.snapTo(1f)
            scale.snapTo(1f)
            offset.animateTo(0f, bounce)
        } else if (indeterminate) {
            alpha.animateTo(
                0f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            )
            scale.snapTo(0f)
            indeterminate = false
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction }.collect { fraction ->
            if (!indeterminate) {
                offset.snapTo(fraction.coerceIn(0f, 3f))
                alpha.snapTo(fraction.coerceIn(0f, 1f))
                scale.snapTo(fraction.coerceIn(0f, 1f))
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction }.map { it >= 1f }
            .distinctUntilChanged().collect { threshold ->
                if (threshold && !indeterminate) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
            }
    }
    if (alpha.value > 0f) Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            .graphicsLayer {
                translationY = offset.value * 60.dp.toPx()
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .size(48.dp)
            .testTag("train-list-refresh-indicator")
            .semantics {
                contentDescription = if (refreshing) "正在刷新车次"
                else if (state.distanceFraction >= 1f) "松开刷新" else "下拉刷新"
            },
        color = MaterialTheme.colorScheme.primaryFixedDim,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (indeterminate) {
                val pop = remember { Animatable(1f) }
                LaunchedEffect(Unit) { pop.animateTo(1.2f, bounce) }
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.onPrimaryFixedVariant,
                    modifier = Modifier.size(38.dp).scale(pop.value),
                )
            } else LoadingIndicator(
                progress = { offset.value },
                color = MaterialTheme.colorScheme.onPrimaryFixedVariant,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

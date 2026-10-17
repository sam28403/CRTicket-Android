package cc.samlab.ticket.ui.monitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/** The indicator enters from the top edge of the page, including its toolbar. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.MonitorRefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val fraction = state.distanceFraction.coerceIn(0f, 1.5f)
    val position by animateFloatAsState(
        if (refreshing) 1f else fraction,
        spring(dampingRatio = 0.8f), label = "monitor-refresh-entry",
    )
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }.distinctUntilChanged().collect {
            if (it) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    if (position > 0.001f || refreshing) Surface(
        modifier = Modifier.align(Alignment.TopCenter).size(48.dp).graphicsLayer {
            translationY = (-56f + position * 80f).dp.toPx()
        }.testTag("monitor-refresh-indicator").semantics {
            contentDescription = if (refreshing) "正在刷新余票" else "下拉刷新"
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryFixedDim,
        shadowElevation = 3.dp,
    ) {
        if (refreshing) LoadingIndicator(color = MaterialTheme.colorScheme.onPrimaryFixedVariant)
        else LoadingIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.onPrimaryFixedVariant,
        )
    }
}

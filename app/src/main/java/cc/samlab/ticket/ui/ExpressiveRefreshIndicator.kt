package cc.samlab.ticket.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveRefreshIndicator(
    progress: Float, refreshing: Boolean, dark: Boolean, pureBlack: Boolean = false,
) {
    val refreshDescription = stringResource(
        if (refreshing) R.string.refresh_loading
        else if (progress >= 1f) R.string.refresh_release else R.string.refresh_pull,
    )
    val context = LocalContext.current
    val colors = homeColorScheme(context, dark, pureBlack)
    MaterialExpressiveTheme(colorScheme = colors) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.TopCenter) {
            val modifier = Modifier.graphicsLayer {
                translationY = (-52 + progress.coerceAtMost(1f) * 76).dp.toPx()
            }.semantics {
                contentDescription = refreshDescription
            }
            if (refreshing) {
                ContainedLoadingIndicator(modifier = modifier)
            } else {
                ContainedLoadingIndicator(progress = { progress }, modifier = modifier)
            }
        }
    }
}

package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val SettingsCollapsedHeight = 56.dp
internal val SettingsExpandedHeight = 179.dp

/**
 * AOSP SettingsLib starts collapsed, then uses a pinned toolbar and a separate expanded title.
 * Match its 20/36 title sizes and 179dp expanded height with Material's nested-scroll app bar.
 * Source: frameworks/base, 7e207744232a, packages/SettingsLib/CollapsingToolbarBaseActivity/res/.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsHeader(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TwoRowsTopAppBar(
        title = { expanded ->
            Text(
                "设置",
                modifier = if (expanded) Modifier.padding(start = 8.dp, bottom = 32.dp) else Modifier,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (expanded) 36.sp else 20.sp,
                    lineHeight = if (expanded) 44.sp else 28.sp,
                ),
                maxLines = 1,
            )
        },
        navigationIcon = {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(20.dp))
            }
        },
        collapsedHeight = SettingsCollapsedHeight,
        expandedHeight = SettingsExpandedHeight,
        // The Activity already applies status bar and display cutout insets to the container.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surfaceContainer,
            scrolledContainerColor = colors.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
        modifier = Modifier.testTag("settings-header"),
    )
}

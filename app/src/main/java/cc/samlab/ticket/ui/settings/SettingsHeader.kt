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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R

internal val SettingsCollapsedHeight = 56.dp
internal val SettingsExpandedHeight = 179.dp

/**
 * AOSP SettingsLib starts collapsed, then uses a pinned toolbar and a separate expanded title.
 * Match its 20/36 title sizes and 179dp expanded height with Material's nested-scroll app bar.
 * Source: frameworks/base, 7e207744232a, packages/SettingsLib/CollapsingToolbarBaseActivity/res/.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsHeader(
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    embedded: Boolean = false,
    title: String = stringResource(R.string.home_settings),
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    TwoRowsTopAppBar(
        title = { expanded ->
            Text(
                title,
                modifier = if (expanded) Modifier.padding(start = 8.dp, bottom = 32.dp)
                    else Modifier.padding(start = if (embedded) 8.dp else 16.dp),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (expanded) 36.sp else 20.sp,
                    lineHeight = if (expanded) 44.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        },
        navigationIcon = {
            if (!embedded) FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back), Modifier.size(20.dp))
            }
        },
        actions = actions,
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

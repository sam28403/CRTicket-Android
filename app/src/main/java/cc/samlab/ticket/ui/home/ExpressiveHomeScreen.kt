package cc.samlab.ticket.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveHomeScreen(
    dark: Boolean,
    visible: Boolean,
    onNavigate: (String) -> Unit,
    pureBlack: Boolean = false,
) {
    val homeFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible) {
        if (visible) {
            // Showing home should not focus the search field or automatically open the IME.
            homeFocus.requestFocus()
            keyboard?.hide()
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier.fillMaxSize().background(colors.surfaceContainer)
                .focusRequester(homeFocus).focusable(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeHeader()
                HomeSearchField()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ticketDestinations.forEachIndexed { index, destination ->
                        val iconColors = homeIconColors(colors, index)
                        HomeEntry(
                            title = stringResource(destination.title),
                            subtitle = stringResource(destination.description),
                            icon = destination.icon,
                            shape = homeEntryShape(index, ticketDestinations.size),
                            iconContainer = iconColors.container,
                            iconTint = iconColors.foreground,
                            onClick = { onNavigate(destination.route) },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HomeEntry(
                        title = stringResource(R.string.home_settings),
                        subtitle = stringResource(R.string.home_settings_description),
                        icon = R.drawable.ic_home_settings,
                        shape = homeEntryShape(0, 2),
                        iconContainer = homeIconColors(colors, 6).container,
                        iconTint = homeIconColors(colors, 6).foreground,
                        onClick = { onNavigate("/settings") },
                    )
                    HomeEntry(
                        title = stringResource(R.string.home_about),
                        subtitle = stringResource(R.string.home_about_description),
                        icon = R.drawable.ic_home_about,
                        shape = homeEntryShape(1, 2),
                        iconContainer = homeIconColors(colors, 7).container,
                        iconTint = homeIconColors(colors, 7).foreground,
                        onClick = { onNavigate("/about") },
                    )
                }
            }
        }
    }
}

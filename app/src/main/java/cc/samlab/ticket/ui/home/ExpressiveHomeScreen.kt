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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/** Shared by compact home and the persistent wide navigation menu. */
internal class HomeNavigationState {
    val scroll = androidx.compose.foundation.ScrollState(0)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveHomeScreen(
    dark: Boolean,
    visible: Boolean,
    onNavigate: (String) -> Unit,
    pureBlack: Boolean,
    navigation: HomeNavigationState,
    selectedRoute: String = "",
) {
    val homeFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible) {
        if (visible) homeFocus.requestFocus()
        keyboard?.hide()
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (selectedRoute.isNotEmpty()) colors.surface else colors.surfaceContainer,
            ).focusRequester(homeFocus).focusable(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(navigation.scroll)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeHeader()
                HomeSearchField { onNavigate("/search") }
                HomeDestinations(ticketDestinations, 0, onNavigate, selectedRoute)
                HomeUtilities(onNavigate, selectedRoute)
            }
        }
    }
}

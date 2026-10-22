package cc.samlab.ticket.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.window.layout.FoldingFeature
import cc.samlab.ticket.ui.navigation.ExpressiveNavigator
import cc.samlab.ticket.ui.navigation.ExpressiveNavHost
import cc.samlab.ticket.ui.navigation.ExpressiveRoutes
import cc.samlab.ticket.ui.notice.ImportantNoticeDialog
import cc.samlab.ticket.ui.window.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveAppLayout(
    location: String,
    dark: Boolean,
    pureBlack: Boolean,
    folds: List<FoldingFeature>,
    navigation: HomeNavigationState,
    navigator: ExpressiveNavigator,
    onNavigate: (String) -> Unit,
    onDestination: (String, Boolean, Boolean) -> Unit,
    content: @Composable (String, Boolean, Boolean) -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val latestDestination by rememberUpdatedState(onDestination)
    val latestLocation by rememberUpdatedState(location)
    // Moving between compact and dual-pane layouts must preserve the graph itself.
    val graph = remember(navigator) {
        movableContentOf<Boolean> { embedded ->
            LaunchedEffect(embedded, latestLocation) {
                if (embedded && latestLocation == "/home") navigator.navigate("/")
            }
            ExpressiveNavHost(navigator, embedded, { destination, back, mirror ->
                latestDestination(destination, back, mirror)
            }) { destination, active -> latestContent(destination, active, embedded) }
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(
        LocalContext.current,
        dark || location == "/railfan/speedometer/live",
        pureBlack || location == "/railfan/speedometer/live",
    )) {
        ImportantNoticeDialog()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            CompositionLocalProvider(LocalFoldingFeatures provides folds) {
                val native = ExpressiveRoutes.isNative(location)
                // A web-only destination fills the window; native pages share the menu.
                CompositionLocalProvider(LocalSinglePane provides !native) {
                    AdaptivePanes(
                        modifier = Modifier.fillMaxSize(),
                        firstPaneFraction = if (folds.any {
                            it.orientation == FoldingFeature.Orientation.VERTICAL
                        }) 0.5f else 1f / 3f,
                        splitAtVerticalFold = true,
                        single = { graph(false) },
                        first = {
                            CompositionLocalProvider(LocalSinglePane provides true) {
                                Box(Modifier.fillMaxSize().testTag("navigation-menu")) {
                                    val selected = when {
                                        location.startsWith("/about") -> "/about"
                                        location.startsWith("/settings") -> "/settings"
                                        location.startsWith("/monitor") -> "/monitor"
                                        location.startsWith("/history") -> "/history"
                                        location.startsWith("/railfan/speedometer") -> "/railfan/speedometer"
                                        location == "/home" -> "/"
                                        else -> ExpressiveRoutes.path(location)
                                    }
                                    ExpressiveHomeScreen(
                                        dark, true, onNavigate, pureBlack, navigation, selected,
                                    )
                                }
                            }
                        },
                        second = {
                            CompositionLocalProvider(LocalSinglePane provides true) {
                                Box(Modifier.fillMaxSize().testTag("navigation-shell")) {
                                    Box(Modifier.fillMaxSize().testTag("navigation-detail")) { graph(true) }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

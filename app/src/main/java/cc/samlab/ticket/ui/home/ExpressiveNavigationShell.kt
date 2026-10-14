package cc.samlab.ticket.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import cc.samlab.ticket.ui.settings.ExpressiveSettingsScreen
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.window.AdaptivePanes
import cc.samlab.ticket.ui.window.LocalFoldingFeatures
import cc.samlab.ticket.ui.window.LocalSinglePane

/** The Web router still owns history; only native routes share this wide-window shell. */
@Composable
internal fun ExpressiveNavigationShell(
    route: String,
    dark: Boolean,
    pureBlack: Boolean,
    settings: NativeSettingsController,
    backProgress: Float,
    backDirection: Float,
    navigation: HomeNavigationState,
    dataVersions: AboutDataVersions,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onExternal: (String) -> Unit,
) {
    val detail = if (route == "/settings") "/settings" else "/about"
    val foldable = LocalFoldingFeatures.current.any {
        it.orientation == FoldingFeature.Orientation.VERTICAL
    }
    val progress = animateFloatAsState(backProgress, tween(100), label = "pane-back").value
    AdaptivePanes(
        modifier = Modifier.fillMaxSize().testTag("navigation-shell"),
        firstPaneFraction = if (foldable) 0.5f else 1f / 3f,
        splitAtVerticalFold = true,
        single = {}, // The host only selects the shell when two usable panes fit.
        first = {
            CompositionLocalProvider(LocalSinglePane provides true) {
                Box(Modifier.fillMaxSize().testTag("navigation-menu")) {
                    ExpressiveHomeScreen(dark, true, { destination ->
                        if (destination != detail) onNavigate(destination)
                        else if (detail == "/settings") settings.ui.closePermissions()
                    }, pureBlack, navigation, selectedRoute = detail)
                }
            }
        },
        second = {
            Box(
                Modifier.fillMaxSize().testTag("navigation-detail").graphicsLayer {
                    scaleX = 1f - progress * 0.06f
                    scaleY = 1f - progress * 0.06f
                    translationX = backDirection * progress * 24.dp.toPx()
                    shape = RoundedCornerShape(28.dp)
                    clip = progress > 0f
                },
            ) {
                CompositionLocalProvider(LocalSinglePane provides true) {
                    AnimatedContent(
                        targetState = detail,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(240)) { it / 30 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "native-detail",
                    ) { page ->
                        if (page == "/settings") ExpressiveSettingsScreen(
                            dark, settings, onBack, pureBlack, route == "/settings", embedded = true,
                        ) else ExpressiveAboutScreen(
                            dark, onBack, onExternal, pureBlack, embedded = true, dataVersions = dataVersions,
                        )
                    }
                }
            }
        },
    )
}

package cc.samlab.ticket.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Real outgoing and incoming destinations remain drawn together throughout a return. */
@Composable
internal fun ExpressiveNavHost(
    navigator: ExpressiveNavigator,
    onDestination: (String, Boolean, Boolean) -> Unit,
    content: @Composable (String, Boolean) -> Unit,
) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val callback by rememberUpdatedState(onDestination)
    val latestContent by rememberUpdatedState(content)
    val enter = ExpressiveRouteMotion.EnterDuration
    val back = ExpressiveRouteMotion.ReturnDuration
    val easing = ExpressiveRouteMotion.Easing
    DisposableEffect(nav, navigator) {
        navigator.attach(nav) { location, popped, mirror -> callback(location, popped, mirror) }
        onDispose { navigator.detach() }
    }
    NavHost(
        navController = nav,
        startDestination = "/home?location={location}",
        modifier = Modifier.fillMaxSize().testTag("expressive-navigation"),
        enterTransition = {
            fadeIn(tween(enter, easing = easing)) +
                scaleIn(tween(enter, easing = easing), initialScale = ExpressiveRouteMotion.BackgroundScale)
        },
        exitTransition = {
            fadeOut(tween(ExpressiveRouteMotion.FadeDuration, easing = easing), targetAlpha = 0.7f) +
                scaleOut(tween(enter, easing = easing), targetScale = ExpressiveRouteMotion.BackgroundScale)
        },
        popEnterTransition = {
            fadeIn(tween(ExpressiveRouteMotion.FadeDuration, easing = easing), initialAlpha = 0.7f) +
                scaleIn(tween(back, easing = easing), initialScale = ExpressiveRouteMotion.BackgroundScale)
        },
        popExitTransition = {
            fadeOut(tween(back, easing = easing)) +
                scaleOut(tween(back, easing = easing), targetScale = ExpressiveRouteMotion.ReturnScale)
        },
    ) {
        (ExpressiveRoutes.pages + "/web").forEach { page ->
            composable(
                route = "$page?location={location}",
                arguments = listOf(navArgument("location") {
                    type = NavType.StringType
                    defaultValue = page
                }),
            ) { entry ->
                val location = ExpressiveRoutes.location(entry)
                Box(
                    Modifier.fillMaxSize().testTag("navigation-page:$location").graphicsLayer {
                        shape = RoundedCornerShape(28.dp)
                        clip = transition.currentState != EnterExitState.Visible ||
                            transition.targetState != EnterExitState.Visible
                    },
                ) { latestContent(location, current?.id == entry.id) }
            }
        }
    }
}

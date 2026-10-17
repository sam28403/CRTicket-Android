package cc.samlab.ticket.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavType
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Uses Navigation's stack and saved-state owners, with separate gesture and release clocks. */
@Composable
internal fun ExpressiveNavHost(
    navigator: ExpressiveNavigator,
    onDestination: (String, Boolean, Boolean) -> Unit,
    content: @Composable (String, Boolean) -> Unit,
) {
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current
    val viewModels = checkNotNull(LocalViewModelStoreOwner.current)
    nav.setViewModelStore(viewModels.viewModelStore)
    val graph = remember(nav) {
        nav.createGraph(startDestination = "/home?location={location}") {
            (ExpressiveRoutes.pages + "/web").forEach { page ->
                composable(
                    route = "$page?location={location}",
                    arguments = listOf(navArgument("location") {
                        type = NavType.StringType
                        defaultValue = page
                    }),
                ) { }
            }
        }
    }
    nav.graph = graph
    val composeNavigator = remember(nav) {
        nav.navigatorProvider.getNavigator<ComposeNavigator>("composable")
    }
    val stack by composeNavigator.backStack.collectAsState()
    val current = stack.lastOrNull()
    val motion = remember(nav) { ExpressiveNavigationMotion(nav, composeNavigator) }
    val savedState = rememberSaveableStateHolder()
    val callback by rememberUpdatedState(onDestination)
    val latestContent by rememberUpdatedState(content)
    val context = LocalContext.current
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val system = remember(context, density, size) {
        SystemRouteAnimations(context, size.width, size.height)
    }
    val scrim = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.8f else 0.2f
    SideEffect {
        motion.size = size
        motion.density = density.density
        motion.scrim = scrim
    }
    DisposableEffect(nav, lifecycle) {
        nav.setLifecycleOwner(lifecycle)
        onDispose { }
    }
    DisposableEffect(nav, navigator) {
        navigator.attach(nav) { location, popped, mirror -> callback(location, popped, mirror) }
        onDispose { navigator.detach() }
    }
    LaunchedEffect(current?.id) { motion.navigate(stack, system) }
    PredictiveBackHandler(enabled = stack.size > 1) { events ->
        motion.back { onProgress -> events.collect { onProgress(it) } }
    }
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .testTag("expressive-navigation")
            .onSizeChanged { size = it },
    ) {
        val scene = motion.scene
        val entries = if (scene == null) {
            listOfNotNull(current).map { it to RouteFrame() }
        } else if (scene.back) {
            listOf(scene.incoming to scene.entering, scene.outgoing to scene.closing)
        } else {
            listOf(scene.outgoing to scene.closing, scene.incoming to scene.entering)
        }
        entries.forEach { (entry, frame) ->
            key(entry.id) {
                val location = ExpressiveRoutes.location(entry)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = frame.x
                        translationY = frame.y
                        scaleX = frame.scale
                        scaleY = frame.scale
                        alpha = frame.alpha
                        shape = RoundedCornerShape(28.dp)
                        clip = scene?.predictive == true
                    }.testTag("navigation-page:$location").drawWithContent {
                        drawContent()
                        if (frame.dim > 0f) drawRect(Color.Black.copy(alpha = frame.dim))
                    },
                ) {
                    entry.LocalOwnersProvider(savedState) {
                        latestContent(location, current?.id == entry.id)
                    }
                }
            }
        }
    }
}

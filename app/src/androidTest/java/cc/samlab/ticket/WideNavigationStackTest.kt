package cc.samlab.ticket

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.navigation.ExpressiveNavHost
import cc.samlab.ticket.ui.navigation.ExpressiveNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideNavigationStackTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun trainStationTrainStopsAtFirstTrainOnLargeScreens() = withGraph(true) { nav ->
        nav.navigate("/train")
        nav.navigate("/train?train=G1", replace = true)
        nav.navigate("/station?station=北京南")
        nav.navigate("/train?train=G3")
        assertTrue(nav.pop())
        assertEquals("/station?station=北京南", nav.currentLocation)
        assertTrue(nav.pop())
        assertEquals("/train?train=G1", nav.currentLocation)
        assertFalse(nav.pop())
    }

    @Test fun historyReplacesTheGeneratorJourneyOnLargeScreens() = withGraph(true) { nav ->
        nav.navigate("/")
        nav.navigate("/?generated=true", replace = true)
        nav.navigate("/history")
        assertFalse(nav.pop())
        assertEquals("/history", nav.currentLocation)
    }

    @Test fun nestedSettingsStillReturnToTheirParent() = withGraph(true) { nav ->
        nav.navigate("/settings")
        nav.navigate("/settings/arrival")
        nav.navigate("/settings/permissions")
        assertTrue(nav.pop())
        assertEquals("/settings/arrival", nav.currentLocation)
        assertTrue(nav.pop())
        assertEquals("/settings", nav.currentLocation)
        assertFalse(nav.pop())
    }

    @Test fun reportReturnsToAboutOnLargeScreens() = withGraph(true) { nav ->
        nav.navigate("/about")
        nav.navigate("/about/report")
        assertTrue(nav.pop())
        assertEquals("/about", nav.currentLocation)
        assertFalse(nav.pop())
    }

    @Test fun phoneKeepsTheGeneratorAndHomeInItsBackStack() = withGraph(false) { nav ->
        nav.navigate("/")
        nav.navigate("/history")
        assertTrue(nav.pop())
        assertEquals("/", nav.currentLocation)
        assertTrue(nav.pop())
        assertEquals("/home", nav.currentLocation)
        assertFalse(nav.pop())
    }

    private fun withGraph(largeScreen: Boolean, check: (ExpressiveNavigator) -> Unit) {
        val navigator = ExpressiveNavigator()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    ExpressiveNavHost(navigator, largeScreen, { _, _, _ -> }) { _, _ ->
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
            compose.waitForIdle()
            scenario.onActivity { check(navigator) }
            compose.waitForIdle()
        }
    }
}

package cc.samlab.ticket

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.navigation.ExpressiveNavHost
import cc.samlab.ticket.ui.navigation.ExpressiveNavigator
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationCompositionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var welcomeState: WelcomeTestState

    @Before fun prepareWelcome() {
        welcomeState = WelcomeTestState(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @After fun restoreWelcome() {
        welcomeState.close()
    }

    @Test fun entryRetainsSourceAndAnimatesWithoutRecomposingPages() {
        val navigator = ExpressiveNavigator()
        val mounts = mutableMapOf<String, Int>()
        val disposals = mutableMapOf<String, Int>()
        var compositions = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    ExpressiveNavHost(navigator, false, { _, _, _ -> }) { location, _ ->
                        DisposableEffect(location) {
                            mounts[location] = mounts.getOrDefault(location, 0) + 1
                            onDispose {
                                disposals[location] = disposals.getOrDefault(location, 0) + 1
                            }
                        }
                        SideEffect { compositions++ }
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            try {
                scenario.onActivity { navigator.navigate("/about") }
                compose.mainClock.advanceTimeBy(96)
                compose.waitForIdle()
                assertEquals("Source was mounted again at entry", 1, mounts["/home"])
                assertEquals("Source was disposed before animation", null, disposals["/home"])
                assertEquals(1, mounts["/about"])
                val before = compositions
                compose.mainClock.advanceTimeBy(160)
                compose.waitForIdle()
                assertEquals("Animation recomposed page content", before, compositions)
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                assertEquals(1, disposals["/home"])
            } finally {
                compose.mainClock.autoAdvance = true
            }
        }
    }
}

package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalSearchTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun searchGroupsExpandClearAndOpenSettings() {
        val fixture = TabletPageFixture(compose)
        try {
            val assets = fixture.instrumentation.targetContext.assets
            val apiFile = assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains("withCredentials") }
            }
            fixture.evaluate("""
                window.searchFixtureReady = false;
                import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                    const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                    const originalAdapter = api.defaults.adapter;
                    api.defaults.adapter = async config => {
                        if (!['/llt-trains', '/station/discovery'].includes(config.url)) {
                            return originalAdapter(config);
                        }
                        const data = config.url === '/llt-trains'
                            ? {success: true, trains: ['G25','G250','G251','G252','D1','D2','D3','D4']
                                .map(train => ({train, from: '北京南', to: '上海虹桥',
                                    duration: '04:30', mileage: 1000, stationCount: 8}))}
                            : {success: true, date: config.params.date,
                                stations: ['大连','丹东','德州','德州东'].map(station => ({
                                    station, total: 20, origin: 5, terminal: 3,
                                    completionTimes: [], passingWindows: []}))};
                        return {data, config, status: 200, statusText: 'OK', headers: {}};
                    };
                    window.searchFixtureReady = true;
                });
            """.trimIndent())
            compose.waitUntil(10_000) { fixture.evaluate("window.searchFixtureReady") == "true" }
            if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("important-notice-confirm").performClick()
            }
            fixture.navigate("/search")
            compose.waitUntil(10_000) {
                var visible = false
                fixture.instrumentation.runOnMainSync {
                    visible = fixture.activity.window.decorView.rootWindowInsets
                        ?.isVisible(android.view.WindowInsets.Type.ime()) == true
                }
                visible
            }
            compose.onNodeWithTag("search-empty-state").assertIsDisplayed()
            var keyboardTop = 0f
            fixture.instrumentation.runOnMainSync {
                val decor = fixture.activity.window.decorView
                keyboardTop = (decor.height - decor.rootWindowInsets
                    .getInsets(android.view.WindowInsets.Type.ime()).bottom).toFloat()
            }
            for (tag in listOf("global-search-field", "search-empty-state")) {
                assertTrue("$tag overlaps the keyboard", compose.onNodeWithTag(tag)
                    .fetchSemanticsNode().boundsInWindow.bottom <= keyboardTop + 1f)
            }
            fixture.capture("search-empty")
            val field = compose.onNodeWithTag("global-search-field")
            val results = compose.onNodeWithTag("global-search-results")
            field.performTextInput("G25")
            field.performImeAction()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("search-trains-title").fetchSemanticsNodes().isNotEmpty()
            }
            results.performScrollToNode(hasText("G251"))
            compose.onNodeWithText("G252").assertDoesNotExist()
            fixture.capture("search-g25-collapsed")
            results.performScrollToNode(hasTestTag("search-trains-expand"))
            compose.onNodeWithTag("search-trains-expand").performClick()
            results.performScrollToNode(hasText("G252"))
            compose.onNodeWithText("G252").assertIsDisplayed()
            fixture.capture("search-g25-expanded")
            results.performScrollToNode(hasTestTag("search-trains-expand"))
            compose.onNodeWithTag("search-trains-expand").performClick()
            compose.onNodeWithText("G252").assertDoesNotExist()

            field.performTextReplacement("D")
            field.performImeAction()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("D1").fetchSemanticsNodes().isNotEmpty()
            }
            results.performScrollToNode(hasTestTag("search-stations-expand"))
            compose.onNodeWithTag("search-stations-expand").performClick()
            results.performScrollToNode(hasText("德州东"))
            compose.onNodeWithText("德州东").assertIsDisplayed()
            fixture.capture("search-stations-expanded")

            field.performTextReplacement(fixture.activity.getString(R.string.settings_theme))
            field.performImeAction()
            results.performScrollToNode(hasText(fixture.activity.getString(R.string.settings_theme)))
            compose.onAllNodes(hasText(fixture.activity.getString(R.string.settings_theme))
                and hasClickAction() and !hasSetTextAction()).onFirst().performClick()
            compose.onNodeWithText(fixture.activity.getString(R.string.theme_light)).assertIsDisplayed()
            pressBack()
            field.performTextClearance()
            compose.onNodeWithTag("search-empty-state").assertIsDisplayed()
            assertEquals("\"/search\"", fixture.evaluate("location.hash.slice(1)"))
        } finally {
            fixture.close()
        }
    }
}

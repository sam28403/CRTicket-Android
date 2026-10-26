package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalLineSearchDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("android", 0)
    private lateinit var welcome: WelcomeTestState
    private var saved = emptyMap<String, Boolean?>()

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        saved = listOf("railfanToolsEnabled", IMPORTANT_NOTICE_SUPPRESSED).associateWith {
            preferences.all[it] as? Boolean
        }
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true)
            .putBoolean("railfanToolsEnabled", true).commit()
    }

    @After fun restore() {
        welcome.close()
        preferences.edit().apply {
            saved.forEach { (key, value) ->
                if (value == null) remove(key) else putBoolean(key, value)
            }
        }.commit()
    }

    @Test fun homeSearchOpensTheSelectedLineAndPreservesResultsOnReturn() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.evaluate("window.crticketNavigate('/home')")
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithContentDescription(fixture.activity.getString(R.string.home_search))
                        .assertIsDisplayed()
                }.isSuccess
            }
            compose.onNodeWithContentDescription(fixture.activity.getString(R.string.home_search)).performClick()
            compose.onNodeWithTag("global-search-field").performTextReplacement("沪昆")
            compose.onNodeWithTag("global-search-field").performImeAction()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("search-line:沪昆高速线").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("search-line:沪昆线").assertIsDisplayed()
            compose.onNodeWithTag("search-line:沪昆高速线").assertIsDisplayed()
            compose.onNodeWithTag("search-lines-group")
                .assert(hasAnyDescendant(hasTestTag("search-line:沪昆线")))
                .assert(hasAnyDescendant(hasTestTag("search-line:沪昆高速线")))
            fixture.capture("global-line-search")
            compose.onNodeWithTag("search-line:沪昆高速线").performClick()
            compose.waitUntil(40_000) {
                compose.onAllNodesWithTag("line-stations").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("line-search").assertTextContains("沪昆高速线")
            compose.onNodeWithText("上海虹桥", substring = false).assertIsDisplayed()
            fixture.capture("global-line-search-detail")
            fixture.instrumentation.runOnMainSync {
                fixture.activity.onBackPressedDispatcher.onBackPressed()
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("global-search-field").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("global-search-field").assertTextContains("沪昆")
            compose.onNodeWithTag("search-line:沪昆线").assertIsDisplayed()
            fixture.instrumentation.runOnMainSync { RailfanPreferences.setEnabled(fixture.activity, false) }
            compose.onNodeWithTag("search-line:沪昆线").assertDoesNotExist()
            compose.onNodeWithTag("search-line:沪昆高速线").assertDoesNotExist()
        } finally {
            fixture.close()
        }
    }
}

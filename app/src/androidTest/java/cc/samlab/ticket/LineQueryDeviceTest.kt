package cc.samlab.ticket

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.railfan.LineQueryScreen
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.junit.Before
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LineQueryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var welcome: WelcomeTestState
    private var previousNotice: Boolean? = null

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", 0)
        previousNotice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
    }

    @After fun restore() {
        welcome.close()
        context.getSharedPreferences("android", 0).edit().apply {
            previousNotice?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
        }.commit()
    }

    @Test
    fun wideScreenUsesContinuousSnakeRows() {
        assumeTrue("Requires a connected tablet", context.resources.configuration.smallestScreenWidthDp >= 600)
        var fixture: TabletPageFixture? = null
        try {
            val page = TabletPageFixture(compose)
            fixture = page
            page.evaluate("window.crticketNavigate('/railfan/lines')")
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("line-search").assertIsDisplayed() }.isSuccess
            }
            page.instrumentation.runOnMainSync {
                page.activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    LineQueryScreen(false, false, false, true, {})
                }
            }
            compose.onNodeWithTag("line-search").performTextReplacement("京港高速线商合段")
            compose.onAllNodes(hasText("京港高速线商合段") and hasClickAction()).onLast().performClick()
            compose.waitUntil(40_000) {
                compose.onAllNodesWithTag("line-snake").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("149 km").assertIsDisplayed()
            page.capture("line-query-snake")
        } finally {
            fixture?.close()
        }
    }

    @Test
    fun fullCatalogSearchAndLiveScreenshotLine() {
        val fixture = TabletPageFixture(compose)
        val dark = mutableStateOf(false)
        try {
            fixture.evaluate("window.crticketNavigate('/railfan/lines')")
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithTag("line-search").assertIsDisplayed()
                }.isSuccess
            }
            fixture.instrumentation.runOnMainSync {
                fixture.activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    LineQueryScreen(dark.value, false, false, true, {})
                }
            }
            compose.onNodeWithTag("line-search").performTextReplacement("京")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("京沪线").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("京沪线").assertIsDisplayed()
            compose.onNodeWithText("京广线").assertExists()
            compose.onNodeWithText("京哈线").assertExists()
            fixture.capture("line-query-candidates")

            compose.onNodeWithTag("line-search").performTextReplacement("京港高速线商合段")
            compose.onAllNodes(hasText("京港高速线商合段") and hasClickAction()).onLast().performClick()
            compose.waitUntil(40_000) {
                compose.onAllNodesWithTag("line-stations").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("商丘东").assertIsDisplayed()
            val titleLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("line-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(titleLayouts)
            }
            val titleLayout = titleLayouts.single()
            assertEquals("京港高速线商合段", titleLayout.layoutInput.text.text)
            assertEquals(1, titleLayout.lineCount)
            assertFalse(titleLayout.hasVisualOverflow)
            assertFalse(titleLayout.isLineEllipsized(0))
            compose.onNodeWithText("16 km").assertIsDisplayed()
            compose.onNodeWithText("49 km").assertIsDisplayed()
            compose.onNodeWithTag("line-stations").performScrollToNode(hasText("太和东"))
            compose.onNodeWithText("149 km").assertIsDisplayed()
            compose.onNodeWithTag("line-stations").performScrollToIndex(0)
            fixture.capture("line-query-stations")
            fixture.instrumentation.runOnMainSync { dark.value = true }
            fixture.capture("line-query-dark")

            compose.onNodeWithTag("line-search").performTextReplacement("不存在的线路")
            compose.onNodeWithText(fixture.activity.getString(R.string.line_query_no_matches))
                .assertIsDisplayed()
        } finally {
            fixture.close()
        }
    }
}

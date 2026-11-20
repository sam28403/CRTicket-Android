package cc.samlab.ticket

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.components.TrainCodeStrikeWidth
import cc.samlab.ticket.ui.travel.TravelArticle
import cc.samlab.ticket.ui.travel.TravelArticleBody
import cc.samlab.ticket.ui.travel.TravelCardLookup
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class TravelDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun explicitTrainCardUsesMarkdownMetrics() {
        val fixture = TabletPageFixture(compose)
        try {
            val activity = fixture.activity
            val lookup = TravelCardLookup(activity.webView)
            fixture.instrumentation.runOnMainSync {
                activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    MaterialTheme {
                        TravelArticleBody(
                            article = TravelArticle("test.md"),
                            markdown = "{V23,北京,二连,5,771,835}",
                            readingWidth = 400.dp,
                            cardLookup = lookup,
                            refresh = 0,
                            onNavigate = {},
                        )
                    }
                }
            }
            val count = activity.getString(R.string.train_list_station_count, "5")
            val speed = activity.getString(R.string.display_decimal_one, 65.0)
            val metrics = activity.getString(R.string.train_list_metrics, "12:51", "835", speed)
            awaitCardText("train-card:V23", count)
                .assertTextContains("北京").assertTextContains("二连").assertTextContains(metrics)
        } finally {
            fixture.close()
        }
    }

    @Test fun opensGitHubArticleWithImagesAndQueryCards() {
        val fixture = TabletPageFixture(compose)
        val timetable = TravelTimetableFixture(fixture)
        try {
            timetable.install(compose)
            val notice = compose.onNodeWithTag("important-notice-confirm")
            if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                notice.performClick()
            }
            val entry = compose.onNodeWithText(fixture.activity.getString(R.string.travel_title))
            entry.performScrollTo().assertIsDisplayed().performClick()
            val articleEntry = displayed("travel-entry:测试文章.md")
            val density = fixture.activity.resources.displayMetrics.density
            val listWidth = displayed("travel-list").fetchSemanticsNode().boundsInRoot.width
            val availableWidth = listWidth / density - 32f
            val columns = ((availableWidth + 12f) / 260f).toInt().coerceAtLeast(1)
            val cardWidth = (availableWidth - (columns - 1) * 12f) * density / columns
            assertEquals(cardWidth, articleEntry.fetchSemanticsNode().boundsInRoot.width, 2f)
            fixture.capture("travel-list")
            articleEntry.performClick()

            val article = displayed("travel-article")
            val column = article.fetchSemanticsNode().boundsInRoot
            val readingWidth = if (fixture.activity.resources.configuration.smallestScreenWidthDp >= 600) 600f
                else 400f
            assertEquals(min(readingWidth * density, listWidth), column.width, 1f)

            onView(withText(containsString("H1 Title"))).check(matches(isDisplayed()))
            val fullTrain = fixture.activity.getString(R.string.train_list_station_count, "3")
            val inactive = awaitCardText("train-card:V23", fullTrain).assertHasClickAction()
                .assertTextContains("北京").assertTextContains("二连")
            compose.onNode(
                hasText("V23") and hasAnyAncestor(hasTestTag("train-card:V23")), useUnmergedTree = true,
            ).assert(SemanticsMatcher.expectValue(TrainCodeStrikeWidth, 0f))
            article.performScrollToIndex(2)
            compose.waitUntil(40_000) { compose.onAllNodesWithTag("travel-image").onFirst().isDisplayed() }
            article.performScrollToIndex(0)
            fixture.capture("travel-article")
            inactive.performClick()
            displayed("train-query-screen")
            compose.waitUntil(20_000) {
                fixture.evaluate("location.hash.includes('train=V23') && location.hash.includes('date=2026-01-05')") == "true"
            }
            fixture.shell("input keyevent KEYCODE_BACK")

            displayed("travel-article").performScrollToNode(hasTestTag("train-card:V24"))
            val segment = displayed("train-card:V24")
            val section = fixture.activity.getString(R.string.train_list_station_count, "2")
            awaitCardText("train-card:V24", section)
            segment.assertTextContains("二连").assertTextContains("北京")
            segment.performClick()
            displayed("train-query-screen")
            compose.waitUntil(20_000) {
                fixture.evaluate("location.hash.includes('train=V24') && location.hash.includes('source=llt')") == "true"
            }
            fixture.shell("input keyevent KEYCODE_BACK")

            displayed("travel-article").performScrollToNode(hasTestTag("travel-station:上海虹桥"))
            val station = awaitCardText(
                "travel-station:上海虹桥",
                fixture.activity.getString(R.string.train_list_total, 1),
            )
            fixture.capture("travel-cards")
            station.performClick()
            displayed("station-screen")
            fixture.shell("input keyevent KEYCODE_BACK")
            displayed("travel-article")
            fixture.shell("input keyevent KEYCODE_BACK")
            displayed("travel-entry:测试文章.md")
        } finally {
            try {
                timetable.restore()
            } finally {
                fixture.close()
            }
        }
    }

    private fun awaitCardText(tag: String, text: String): SemanticsNodeInteraction {
        val card = compose.onNode(hasTestTag(tag) and hasText(text))
        compose.waitUntil(30_000) { card.isDisplayed() }
        return card
    }

    private fun displayed(tag: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        compose.waitUntil(30_000) { node.isDisplayed() }
        return node
    }
}

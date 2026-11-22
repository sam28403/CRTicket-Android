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
import cc.samlab.ticket.ui.travel.TravelContentRevision
import cc.samlab.ticket.ui.travel.TravelPageState
import cc.samlab.ticket.ui.travel.TravelScreen
import cc.samlab.ticket.ui.travel.sortTravelArticles
import java.io.IOException
import java.time.Instant
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class TravelDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun connectionFailureShowsEmptyStateAndRefreshRecovers() {
        val fixture = TabletPageFixture(compose)
        try {
            val activity = fixture.activity
            val lookup = TravelCardLookup(activity.webView)
            val view = activity.findViewById<ComposeView>(R.id.expressive_navigation_view)
            val pages = listOf("/travel" to "travel-list", "/travel/article?file=test.md" to "travel-article")
            for ((location, contentTag) in pages) {
                var connected = true
                val page = TravelPageState(
                    listArticles = { _, _ ->
                        if (!connected) throw IOException("Connection failed")
                        listOf(TravelArticle("test.md"))
                    },
                    readArticle = { _, _, _ ->
                        if (!connected) throw IOException("Connection failed")
                        "# Test article"
                    },
                )
                fixture.instrumentation.runOnMainSync {
                    view.setContent {
                        TravelScreen(location, false, false, false, lookup, {}, {}, page)
                    }
                }
                displayed(contentTag)
                fixture.instrumentation.runOnMainSync { connected = false }
                compose.onNodeWithContentDescription(activity.getString(R.string.station_refresh)).performClick()
                displayed("travel-empty")
                compose.onNodeWithTag(contentTag).assertDoesNotExist()
                fixture.instrumentation.runOnMainSync { connected = true }
                displayed("travel-empty-refresh").performClick()
                displayed(contentTag)
                compose.onNodeWithTag("travel-empty").assertDoesNotExist()
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun sortsPinnedAndRegularArticlesByLatestModification() {
        val older = Instant.parse("2026-11-01T12:00:00Z")
        val latest = Instant.parse("2026-11-18T12:00:00Z")
        val articles = sortTravelArticles(listOf(
            TravelArticle("甲.md", older),
            TravelArticle("#先前.md", older),
            TravelArticle("乙.md", latest),
            TravelArticle("#最新.md", latest),
            TravelArticle("未读取.md"),
        ))
        assertEquals(listOf("最新", "先前", "乙", "甲", "未读取"), articles.map { it.title })
    }

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
            delayOnlineQuery(fixture)
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
            pullToRefresh()
            fixture.capture("travel-list")
            articleEntry.performClick()

            val article = displayed("travel-article")
            val column = article.fetchSemanticsNode().boundsInRoot
            val readingWidth = if (fixture.activity.resources.configuration.smallestScreenWidthDp >= 600) 600f
                else 400f
            assertEquals(min(readingWidth * density, listWidth), column.width, 1f)

            onView(withText(containsString("H1 Title"))).check(matches(isDisplayed()))
            val fullTrain = fixture.activity.getString(R.string.train_list_station_count, "3")
            awaitCardText("train-card:V23", fullTrain).assertHasClickAction()
                .assertTextContains("北京").assertTextContains("二连")
            compose.onNode(
                hasText("V23") and hasAnyAncestor(hasTestTag("train-card:V23")), useUnmergedTree = true,
            ).assert(SemanticsMatcher.expectValue(TrainCodeStrikeWidth, 0f))
            article.performScrollToIndex(2)
            compose.waitUntil(40_000) { compose.onAllNodesWithTag("travel-image").onFirst().isDisplayed() }
            article.performScrollToIndex(0)
            pullToRefresh()
            val lastRefresh = revision()
            fixture.capture("travel-article")

            displayed("travel-article").performScrollToNode(hasTestTag("train-card:V24"))
            val segment = displayed("train-card:V24")
            val section = fixture.activity.getString(R.string.train_list_station_count, "2")
            awaitCardText("train-card:V24", section)
            segment.assertTextContains("二连").assertTextContains("北京")
            val trainOffset = segment.fetchSemanticsNode().boundsInRoot.top
            segment.performClick()
            displayed("train-query-screen")
            compose.waitUntil(20_000) {
                fixture.evaluate(
                    "location.hash.includes('train=V24') && location.hash.includes('source=online') " +
                        "&& window.travelOnlineQuery === 'V24'",
                ) == "true"
            }
            fixture.shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(20_000) { fixture.evaluate("location.hash === '#/home'") == "true" }
            fixture.evaluate("window.failTravelOnlineQuery()")
            assertEquals(trainOffset, displayed("train-card:V24").fetchSemanticsNode().boundsInRoot.top, 2f)
            assertEquals(lastRefresh, revision())

            displayed("travel-article").performScrollToIndex(6)
            val stationTag = "travel-station:上海虹桥"
            val station = awaitCardText(
                stationTag,
                fixture.activity.getString(R.string.train_list_total, 1),
            )
            fixture.capture("travel-cards")
            val stationOffset = station.fetchSemanticsNode().boundsInRoot.top
            station.performClick()
            displayed("station-screen")
            fixture.shell("input keyevent KEYCODE_BACK")
            assertEquals(stationOffset, displayed(stationTag).fetchSemanticsNode().boundsInRoot.top, 2f)
            assertEquals(lastRefresh, revision())
            fixture.shell("input keyevent KEYCODE_BACK")
            displayed("travel-entry:测试文章.md").performClick()
            displayed("travel-article")
            onView(withText(containsString("H1 Title"))).check(matches(isDisplayed()))
        } finally {
            try {
                timetable.restore()
            } finally {
                fixture.close()
            }
        }
    }

    private fun delayOnlineQuery(fixture: TabletPageFixture) {
        val assets = fixture.instrumentation.targetContext.assets
        val apiFile = assets.list("web/assets")!!.first { name ->
            name.startsWith("api-") && assets.open("web/assets/$name")
                .bufferedReader().use { it.readText().contains("withCredentials") }
        }
        fixture.evaluate("""
            import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                const api = Object.values(module).find(value => value?.defaults?.withCredentials === true)
                const original = api.defaults.adapter
                api.defaults.adapter = config => {
                    if (config.url !== '/train-info') return original(config)
                    window.travelOnlineQuery = config.params.train
                    return new Promise((_, reject) => {
                        window.failTravelOnlineQuery = () => reject(new Error('Delayed online failure'))
                    })
                }
                window.travelOnlineFixtureReady = true
            })
        """.trimIndent())
        compose.waitUntil(10_000) { fixture.evaluate("window.travelOnlineFixtureReady") == "true" }
    }

    private fun pullToRefresh() {
        val saved = revision()
        compose.onNodeWithTag("travel-pull").performTouchInput {
            swipeDown(startY = height * 0.22f, endY = height * 0.82f, durationMillis = 420)
        }
        compose.waitUntil(30_000) { revision() > saved }
    }

    private fun revision(): Int =
        compose.onNodeWithTag("travel-pull").fetchSemanticsNode().config[TravelContentRevision]

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

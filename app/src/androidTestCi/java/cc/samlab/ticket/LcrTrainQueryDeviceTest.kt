package cc.samlab.ticket

import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.network.PhoneHttp
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LcrTrainQueryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun listOpensOnlineTimetableAndFallsBackAfterRequestFails() {
        val fixture = TabletPageFixture(compose)
        val preferences = fixture.activity.getSharedPreferences("developer-options", 0)
        val autoUpdate = preferences.getBoolean("lltAutoUpdate", true)
        val calls = AtomicInteger()
        val unexpected = AtomicInteger()
        try {
            preferences.edit().putBoolean("lltAutoUpdate", false).apply()
            interceptTimetable(fixture.activity, calls, unexpected)
            fixture.evaluate("""
                window.lcrTestNow = Date.parse('2026-11-12T07:00:00Z');
                Date.now = () => window.lcrTestNow;
                const openDatabase = indexedDB.open.bind(indexedDB);
                indexedDB.open = (name, version) => openDatabase(
                    name === 'crticket-phone-timetable' ? 'lcr-train-query-test' : name, version);
                const originalFetch = window.fetch.bind(window);
                window.fetch = (url, options) => url === '/offline/an.db'
                    ? Promise.resolve(new Response(null, {status: 404}))
                    : originalFetch(url, options);
            """.trimIndent())
            dismissNotice()
            fixture.navigate("/list")
            openListTrain("C85")
            assertDeparture("18:43")
            assertEquals(1, calls.get())
            assertEquals("true", fixture.evaluate("location.hash.includes('dataset=lcr')"))
            assertEquals("false", fixture.evaluate("location.hash.includes('source=llt')"))
            fixture.capture("lcr-train-online")

            fixture.evaluate("window.lcrTestNow = Date.parse('2026-11-13T07:00:00Z'); null")
            fixture.navigate("/list")
            openListTrain("C86")
            assertDeparture("15:15")
            assertEquals(2, calls.get())
            assertEquals(0, unexpected.get())
            assertEquals("true", fixture.evaluate("location.hash.includes('dataset=lcr')"))
            assertEquals("false", fixture.evaluate("location.hash.includes('source=llt')"))
            fixture.capture("lcr-train-offline-fallback")
        } finally {
            fixture.evaluate("indexedDB.deleteDatabase('lcr-train-query-test'); null")
            preferences.edit().putBoolean("lltAutoUpdate", autoUpdate).apply()
            fixture.close()
        }
    }

    @Test fun creditsOpenLaosBusTicketsTimetable() {
        val fixture = TabletPageFixture(compose)
        try {
            dismissNotice()
            fixture.navigate("/about")
            val credits = fixture.activity.getString(R.string.about_credits)
            compose.onNodeWithContentDescription(credits).performScrollTo().performClick()
            val grid = hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("credits-screen"))
            val name = "Laos Bus Tickets"
            val url = "https://laosbustickets.com/train-tickets-laos/#timetable"
            compose.waitUntil(15_000) { compose.onAllNodes(grid).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(grid).performScrollToNode(hasText(name))
            compose.onNode(hasText(name) and hasText(url)).assertIsDisplayed()
            fixture.capture("lcr-timetable-credit")
            val filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addDataScheme("https")
                addDataAuthority("laosbustickets.com", null)
                addDataPath("/train-tickets-laos/", PatternMatcher.PATTERN_LITERAL)
            }
            val monitor = fixture.instrumentation.addMonitor(
                filter, android.app.Instrumentation.ActivityResult(0, null), true,
            )
            try {
                compose.onNodeWithText(name).performClick()
                compose.waitForIdle()
                assertEquals(1, monitor.hits)
            } finally {
                fixture.instrumentation.removeMonitor(monitor)
            }
        } finally {
            fixture.close()
        }
    }

    private fun dismissNotice() {
        if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("important-notice-confirm").performClick()
        }
    }

    private fun openListTrain(code: String) {
        val card = hasTestTag("train-card:$code")
        compose.waitUntil(30_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(card).performClick()
    }

    private fun assertDeparture(time: String) {
        val stop = hasTestTag("train-query-stop-0") and hasText(time)
        compose.waitUntil(30_000) {
            runCatching {
                compose.onNodeWithTag("train-query-content").performScrollToNode(stop)
            }.isSuccess
        }
        compose.onNode(stop).assertIsDisplayed()
    }

    private fun interceptTimetable(activity: MainActivity, calls: AtomicInteger, unexpected: AtomicInteger) {
        val delegate = MainActivity::class.java.getDeclaredField("phoneHttp\$delegate").apply {
            isAccessible = true
        }
        val http = (delegate.get(activity) as Lazy<*>).value as PhoneHttp
        val client = PhoneHttp::class.java.getDeclaredField("client").apply { isAccessible = true }
        val original = client.get(http) as OkHttpClient
        client.set(http, original.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.toString() != "https://laosbustickets.com/data/laos-train-timetable.json") {
                unexpected.incrementAndGet()
                throw IOException("Unexpected timetable request: ${request.url}")
            }
            if (calls.incrementAndGet() > 1) throw IOException("Controlled online timetable failure")
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(timetable.toResponseBody("application/json".toMediaType()))
                .build()
        }.build())
    }

    private val timetable = """
        {"checkedAt":"2026-11-11T23:00:07Z","trains":[{"train":"C85","runsOn":["2026-11-12"],"stops":[
          {"code":"LAVJM","country":"LA","utcOffset":"+07:00","depart":"18:43"},
          {"code":"LAVTM","country":"LA","utcOffset":"+07:00","arrive":"20:35"}
        ]}]}
    """.trimIndent()
}

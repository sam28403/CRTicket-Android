package cc.samlab.ticket

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalHistory
import cc.samlab.ticket.arrival.ArrivalNotifications
import cc.samlab.ticket.arrival.ArrivalStore
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisitedStationsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
    private val names = listOf("漠河", "抚远", "徐闻", "三亚", "阿克陶", "吐鲁番北", "根河", "安多")

    @Test
    fun firstVisitsPersistGroupByDateAndOpenStationBoards() {
        val saved = preferences.all.filterKeys { it == "firstVisits" || it.startsWith("notified:") }
        val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = android.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        clearHistory()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/railfan/visits")
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("visits-empty").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithTag("visits-empty").assertIsDisplayed()
            capture(fixture, "visits-empty")
            mockStationSummaries(fixture)
            val dates = listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20))
            val zone = ZoneId.systemDefault()
            val store = ArrivalStore(context)
            names.forEachIndexed { index, name ->
                val time = dates[index / 4].atTime(12, index).atZone(zone).toInstant().toEpochMilli()
                val notification = ArrivalNotifications(context).build(name)
                assertEquals("欢迎到访${name}站", notification.extras.getString(Notification.EXTRA_TITLE))
                store.notified(name, time)
                store.notified(name, time + 86_400_000L)
                assertEquals(time, ArrivalHistory(context).visits().single { it.station == name }.firstArrival)
            }
            assertEquals(8, ArrivalHistory(context).visits().size)
            assertTrue(ArrivalHistory(context).visits().none { it.historicalReminder })
            compose.waitUntil {
                compose.onAllNodesWithTag("visits-empty").fetchSemanticsNodes().isEmpty()
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("共 20 趟").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("首次到访").assertDoesNotExist()
            compose.onNodeWithTag("visit-date:2026-10-21").assertIsDisplayed()
            capture(fixture, "visits-first-date")
            names.forEach { name ->
                compose.onNodeWithTag("visits-timeline")
                    .performScrollToNode(hasTestTag("visited-station:$name"))
                compose.onNodeWithTag("visited-station:$name").assertIsDisplayed()
                compose.onNodeWithTag("visited-station:$name")
                    .assertTextContains("始发 ${if (name == "阿克陶") 0 else 4} 趟", substring = true)
                    .assertTextContains("终到 ${if (name == "阿克陶") 0 else 3} 趟", substring = true)
            }
            compose.onNodeWithTag("visits-timeline")
                .performScrollToNode(hasTestTag("visit-date:2026-10-20"))
            compose.onNodeWithTag("visit-date:2026-10-20").assertIsDisplayed()
            capture(fixture, "visits-second-date")
            val visits = ArrivalHistory(context).visits()
            assertEquals(listOf("三亚", "徐闻", "抚远", "漠河", "安多", "根河", "吐鲁番北", "阿克陶"),
                visits.map { it.station })
            compose.onNodeWithTag("visits-timeline")
                .performScrollToNode(hasTestTag("visited-station:安多"))
            compose.onNodeWithTag("visited-station:安多").performClick()
            compose.waitUntil(15_000) {
                fixture.evaluate("location.hash.includes('station=')") == "true"
            }
            assertTrue(fixture.evaluate("decodeURIComponent(location.hash)").contains("安多"))
            fixture.navigate("/home")
            fixture.navigate("/railfan/visits")
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithTag("visit-date:2026-10-21").assertIsDisplayed()
                }.isSuccess
            }
            compose.onNodeWithTag("visit-date:2026-10-21").assertIsDisplayed()
            capture(fixture, "visits-reopened")
        } finally {
            fixture.close()
            clearHistory()
            val editor = preferences.edit()
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.commit()
            android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
        }
    }

    private fun clearHistory() {
        val editor = preferences.edit()
        preferences.all.keys.filter { it == "firstVisits" || it.startsWith("notified:") }
            .forEach { editor.remove(it) }
        editor.commit()
    }

    private fun mockStationSummaries(fixture: TabletPageFixture) {
        val assets = context.assets
        val apiFile = assets.list("web/assets")!!.first { name ->
            name.startsWith("api-") && assets.open("web/assets/$name")
                .bufferedReader().use { it.readText().contains("withCredentials") }
        }
        fixture.evaluate("""
            window.visitCardsReady = false;
            import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                api.defaults.adapter = async config => ({
                    status: 200, statusText: 'OK', headers: {}, config,
                    data: {success: true, date: config.params.date,
                        stations: ['漠河','抚远','徐闻','三亚','阿克陶','吐鲁番北','根河','安多']
                            .filter(station => station !== '阿克陶')
                            .map(station => ({station, total: 20, origin: 4, terminal: 3,
                                completionTimes: Array(10).fill('00:00'),
                                passingWindows: [{arrival: 0, departure: 86400}]}))}
                });
                window.visitCardsReady = true;
            });
        """.trimIndent())
        compose.waitUntil(10_000) { fixture.evaluate("window.visitCardsReady") == "true" }
    }

    private fun capture(fixture: TabletPageFixture, name: String) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "visits-scenes").apply { mkdirs() }
        val view = fixture.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = -1
        fixture.instrumentation.runOnMainSync {
            PixelCopy.request(fixture.activity.window, image, {
                result = it
                latch.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        File(folder, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}

package cc.samlab.ticket

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class QueryBackStackTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixture = MonitorLifecycleDeviceFixture(instrumentation)
    private val date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
    private val preferences = instrumentation.targetContext
        .getSharedPreferences("android", Context.MODE_PRIVATE)
    private var noticeWasSet = false
    private var noticeWasSuppressed = false

    @Before fun suppressStartupNoticeDuringNavigationChecks() {
        noticeWasSet = preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)
        noticeWasSuppressed = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
    }

    @After fun restoreStartupNoticePreference() {
        val editor = preferences.edit()
        if (noticeWasSet) editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, noticeWasSuppressed)
        else editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
        editor.commit()
    }

    @Test fun stationTrainStationReturnsThroughEachVisitedQuery() = fixture.withSnapshot {
        navigate("/station")
        replace(station("北京南"))
        navigate(train("G1"))
        navigate(station("南京南"))
        backTo(train("G1"))
        backTo(station("北京南"))
        assertJourneyEnds()
    }

    @Test fun trainStationTrainReturnsThroughEachVisitedQuery() = fixture.withSnapshot {
        navigate("/train")
        replace(train("G1"))
        navigate(station("北京南"))
        navigate(train("G3"))
        backTo(station("北京南"))
        backTo(train("G1"))
        assertJourneyEnds()
    }

    @Test fun switchingFromGeneratorToHistoryUsesTheWindowBackPolicy() = fixture.withSnapshot {
        navigate("/")
        navigate("/history")
        if (isLargeScreen()) {
            assertHomeExits()
        } else {
            backTo("/")
            backTo("/home")
            assertHomeExits()
        }
    }

    @Test fun repeatedTrackingNotificationAlwaysReturnsDirectlyHome() = fixture.withSnapshot {
        navigate("/train")
        replace(train("G1"))
        fixture.startTrain(JSONObject()
            .put("train", "G1")
            .put("date", date)
            .put("querySource", "llt")
            .put("stops", JSONArray()
                .put(JSONObject().put("station", "北京南").put("departure", "00:00"))
                .put(JSONObject().put("station", "南京南")
                    .put("arrival", "23:59").put("arrivalDay", 1)))
            .toString())
        val notice = requireNotNull(fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID))
        TrackingNotificationTap(instrumentation).use { tap ->
            repeat(3) {
                fixture.shell("input keyevent KEYCODE_HOME")
                tap.click(notice)
                awaitRoute(train("G1"))
                compose.waitForIdle()
            }
        }
        assertJourneyEnds()
    }

    @Test fun coldTrackingNotificationHasHomeAsItsOnlyParent() = fixture.withSnapshot {
        fixture.open(Intent(fixture.context, MainActivity::class.java)
            .putExtra(TrainTrackingNotifications.OPEN_TRACKING, train("G1")))
        awaitRoute(train("G1"))
        assertJourneyEnds()
    }

    private fun train(code: String) = "/train?train=$code&date=$date&source=llt"
    private fun station(name: String) = "/station?station=${Uri.encode(name)}"

    private fun navigate(route: String) = changeRoute("push", route)
    private fun replace(route: String) = changeRoute("replace", route)

    private fun changeRoute(method: String, route: String) {
        fixture.await("Router was not ready") {
            fixture.evaluate("typeof window.crticketNavigate === 'function'") == "true"
        }
        val script = if (method == "push") {
            "window.crticketNavigate(${JSONObject.quote(route)})"
        } else {
            // Vue Router is also exposed on the mounted app for query form updates.
            "document.querySelector('#app').__vue_app__.config.globalProperties." +
                "\$router.replace(${JSONObject.quote(route)})"
        }
        fixture.evaluate(script)
        awaitRoute(route)
    }

    private fun backTo(route: String) {
        fixture.commitBack()
        compose.waitForIdle()
        awaitRoute(route)
    }

    private fun awaitRoute(route: String) {
        fixture.await("Expected $route; last route ${fixture.evaluate("location.hash")}") {
            fixture.evaluate("location.hash === ${JSONObject.quote("#$route")}") == "true"
        }
        compose.waitForIdle()
    }

    private fun isLargeScreen() =
        compose.onAllNodesWithTag("navigation-menu").fetchSemanticsNodes().isNotEmpty()

    private fun assertJourneyEnds() {
        if (!isLargeScreen()) backTo("/home")
        assertHomeExits()
    }

    private fun assertHomeExits() {
        fixture.commitBack()
        compose.waitForIdle()
        fixture.await("Home back did not close the activity") {
            var resumed = false
            fixture.onMain {
                resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .any { it is MainActivity }
            }
            !resumed
        }
    }
}

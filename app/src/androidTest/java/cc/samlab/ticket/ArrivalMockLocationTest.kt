package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import cc.samlab.ticket.arrival.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** 使用 Android FLP 模拟位置，等待生产接收器发布通知，不伪造围栏广播。 */
@RunWith(AndroidJUnit4::class)
class ArrivalMockLocationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val monitor = StationArrivalMonitor.get(context)
    private val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)

    @Test fun mainlandHongKongAndLaosRealMockLocationNotificationsAndNavigation() {
        testStations(listOf("北京南", "香港西九龙", "万象"))
    }

    @Test fun jinshanweiXiufengAndXinzhuangRealMockLocations() {
        testStations(listOf("金山卫", "绣峰", "莘庄"))
    }

    private fun testStations(names: List<String>) {
        val saved = preferences.all.toMap()
        try {
            assertTrue("需要已有的后台定位和通知权限", ArrivalAccess.ready(context))
            onMain { monitor.stop() }
            Tasks.await(client.setMockMode(true), 15, TimeUnit.SECONDS)
            for (name in names) testStation(name)
        } finally {
            onMain { monitor.stop() }
            runCatching { Tasks.await(client.setMockMode(false), 15, TimeUnit.SECONDS) }
            manager.cancel("station-arrival", 401)
            restorePreferences(saved)
            onMain { monitor.restore() }
        }
    }

    private fun testStation(name: String) {
        val station = ArrivalCatalog.stations(context).single { it.name == name }
        preferences.edit().remove("notified:$name").commit()
        manager.cancel("station-arrival", 401)
        onMain { monitor.stop() }
        // 400 米范围外，随后移动到距离站点 250 米，验证 300 米边界。
        inject(station, 400.0)
        onMain { monitor.start() }
        await("$name 围栏注册", 30_000) { ArrivalStore(context).registered }
        repeat(5) { inject(station, 400.0); Thread.sleep(1000) }
        assertNull("$name 400 米处不应提醒", welcome(name))
        report("$name OUTSIDE 400m: no welcome notification")
        val started = SystemClock.elapsedRealtime()
        await("$name 250 米处的真实定位通知", 240_000, { inject(station, 250.0) }) {
            welcome(name) != null
        }
        val notification = welcome(name)!!
        assertEquals("点按查看当前车站大屏", notification.extras.getString(Notification.EXTRA_TEXT))
        val posted = ArrivalStore(context).lastNotification(name)
        repeat(5) { inject(station, 250.0); Thread.sleep(1000) }
        assertEquals("同站位置更新不得重复提醒", posted, ArrivalStore(context).lastNotification(name))
        report("$name INSIDE 250m: welcome in ${SystemClock.elapsedRealtime() - started}ms")
        notification.contentIntent.send()
        await("$name 通知跳转车站大屏", 30_000) {
            var correct = false
            onMain {
                val activities = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                correct = activities.filterIsInstance<MainActivity>().any {
                    val uri = android.net.Uri.parse(it.webView.url.orEmpty().substringAfter('#'))
                    uri.path == "/station" && uri.getQueryParameter("station") == name
                }
            }
            correct
        }
        report("$name CLICK: station screen route verified; cooldown verified")
    }

    private fun report(message: String) {
        Log.i("ArrivalMockTest", message)
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "$message\n") })
    }

    private fun welcome(name: String): Notification? = manager.activeNotifications
        .map { it.notification }.firstOrNull {
            it.extras.getString(Notification.EXTRA_TITLE) == "欢迎到访${name}站"
        }

    private fun inject(station: ArrivalStation, northMeters: Double) {
        val location = Location("fused").apply {
            latitude = station.latitude + northMeters / 111_195.0
            longitude = station.longitude
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        Tasks.await(client.setMockLocation(location), 15, TimeUnit.SECONDS)
    }

    private fun await(label: String, timeout: Long, tick: () -> Unit = {}, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            tick()
            if (condition()) return
            Thread.sleep(1000)
        }
        fail("超时：$label；状态：${ArrivalStore(context).message}")
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun restorePreferences(saved: Map<String, *>) {
        val editor = preferences.edit().clear()
        saved.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.commit()
    }
}

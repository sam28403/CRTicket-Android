package cc.samlab.ticket

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalCatalog
import cc.samlab.ticket.arrival.ArrivalNotifications
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationArrivalNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun welcomeNotificationsHaveCorrectTextAndDistinctStationDestinations() {
        val notifications = ArrivalNotifications(context)
        notifications.createChannel()
        val beijing = notifications.build("北京南")
        val shanghai = notifications.build("上海虹桥")
        assertEquals("欢迎到访北京南站", beijing.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("点按查看当前车站大屏", beijing.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(ArrivalNotifications.CHANNEL, beijing.channelId)
        assertTrue(beijing.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNotNull(beijing.contentIntent)
        assertNotEquals(beijing.contentIntent, shanghai.contentIntent)
    }

    @Test fun nativeCatalogContainsOnlyNamedStationsWithValidWgs84Coordinates() {
        val stations = ArrivalCatalog.stations(context)
        assertTrue(stations.size > 3000)
        assertEquals(stations.size, stations.map { it.name }.toSet().size)
        assertTrue(stations.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
        val beijing = stations.single { it.name == "北京南" }
        assertTrue(beijing.latitude in 39.0..41.0)
        assertTrue(beijing.longitude in 115.0..117.0)
    }
}

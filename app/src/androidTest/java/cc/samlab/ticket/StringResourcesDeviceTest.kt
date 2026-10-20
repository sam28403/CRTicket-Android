package cc.samlab.ticket

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.railway.TrainNumberRules
import cc.samlab.ticket.tracking.TrainTrackingText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringResourcesDeviceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = target.createConfigurationContext(
        Configuration(target.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("zh-CN"))
        },
    )

    @Test
    fun packagedResourcesPreserveSpacingNewlinesAndArguments() {
        assertEquals(
            "你已经运转了 3 次，120 公里，共消费 50 元！",
            context.getString(R.string.history_summary, 3, "120", "50"),
        )
        assertEquals(
            "当前到站：济南西，0h5m后出发\n前方到站：上海虹桥，预计到达：11:05",
            context.getString(
                R.string.tracking_notification_at_station,
                "济南西", "0h5m", "上海虹桥", "11:05",
            ),
        )
        assertEquals("积分 500", context.getString(R.string.history_credit_amount, "500"))
        assertEquals("¥5", context.getString(R.string.history_price_amount, "5"))
    }

    @Test
    fun applicationInitializesResourcesForValidationAndNotifications() {
        assertEquals(target.getString(R.string.train_error_required), TrainNumberRules.error(""))
        assertEquals(
            target.getString(R.string.duration_hours_minutes, 1, 5),
            TrainTrackingText.duration(65 * 60_000L),
        )
        assertEquals(
            target.getString(R.string.duration_compact_hours_minutes, 1, 5),
            TrainTrackingText.compactDuration(65 * 60_000L),
        )
    }
}

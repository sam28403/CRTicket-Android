package cc.samlab.ticket

import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDownloadDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun downloadOpensSystemFilesOverHistory() {
        val fixture = TabletPageFixture(compose)
        val activity = fixture.activity
        val automation = fixture.instrumentation.uiAutomation
        val id = java.util.UUID.randomUUID().toString()
        val storage = "crticket-device-tickets-v1"
        fun pickerOpen() = automation.rootInActiveWindow?.packageName
            ?.toString()?.endsWith("documentsui") == true
        try {
            fixture.evaluate("""
                const rows = JSON.parse(localStorage.getItem('$storage') || '[]');
                rows.push({id:'$id', ticket_number:'E123456789', train_no:'G103',
                    departure_station:'北京南', arrival_station:'上海虹桥', seat_no:'05车08A号',
                    travel_date:'2026年11月03日', departure_time:'09:00', price:553, seat_type:'二等座'});
                localStorage.setItem('$storage', JSON.stringify(rows));
            """.trimIndent())
            fixture.navigate("/history")
            val record = hasTestTag("history-record-$id")
            compose.onNodeWithTag("history-list").performScrollToNode(record)
            compose.onNode(hasContentDescription(activity.getString(R.string.history_record_actions))
                and hasAnyAncestor(record)).performClick()
            compose.onNodeWithText(activity.getString(R.string.history_download)).performClick()
            compose.waitUntil(30_000) {
                assertTrue(activity.findViewById<android.view.View>(
                    R.id.expressive_navigation_view,
                ).isShown)
                pickerOpen()
            }
            val file = activity.cacheDir.listFiles().orEmpty()
                .filter { it.name.startsWith("export-") }.maxBy { it.lastModified() }
            val image = BitmapFactory.decodeFile(file.path)
            assertEquals(860, image.width)
            assertEquals(540, image.height)
            image.recycle()
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitUntil(10_000) { activity.hasWindowFocus() }
            compose.onNodeWithTag("history-list").assertIsDisplayed()
        } finally {
            if (pickerOpen()) automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            fixture.evaluate("""
                localStorage.setItem('$storage', JSON.stringify(
                    JSON.parse(localStorage.getItem('$storage') || '[]').filter(row => row.id !== '$id')
                ));
            """.trimIndent())
            fixture.close()
        }
    }
}

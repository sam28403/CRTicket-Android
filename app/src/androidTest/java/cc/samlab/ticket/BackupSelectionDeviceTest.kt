package cc.samlab.ticket

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSelectionDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var welcomeState: WelcomeTestState? = null

    @Before fun prepareWelcome() { welcomeState = WelcomeTestState(context) }
    @After fun restoreWelcome() { welcomeState?.close() }
    private val excluded = setOf(
        "车票历史记录", "设置项", "个人资料", "到访车站记录",
        "测速记录与轨迹", "车票生成草稿", "余票监控配置",
    )

    @Test fun searchOnlyExportAndImportPreserveUnselectedData() {
        val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = android.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val history = context.getSharedPreferences("vehicle-query", Context.MODE_PRIVATE)
        val originalHistory = history.getString("history", null)
        android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity {
                    activity = it
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val fixture = BackupDeviceFixture(compose, activity)
                fixture.ready()
                compose.onNodeWithText("导出备份").performScrollTo().performClick()
                (excluded + "搜索记录").forEach { label ->
                    compose.onNode(hasText(label) and isToggleable()).assertIsOn().performScrollTo().performClick()
                }
                compose.onNodeWithText("开始导出").assertIsNotEnabled()
                compose.onNodeWithText("取消").performClick()
                val storage = fixture.storage()
                try {
                    val full = fixture.export()
                    val partial = fixture.export(excluded)
                    assertEquals("[\"search\"]", partial.getJSONArray("contents").toString())
                    assertTrue(partial.isNull("user"))
                    assertEquals(0, partial.getJSONArray("tickets").length())
                    val settings = partial.getJSONObject("settings")
                    assertFalse(settings.has("profile"))
                    assertFalse(settings.has("theme"))
                    assertEquals(setOf("vehicleSearchHistory", "saleSearchHistory"),
                        settings.getJSONObject("native").keys().asSequence().toSet())
                    assertEquals(2, settings.getJSONObject("storage").length())

                    val source = full.getJSONObject("settings")
                    source.getJSONObject("profile").put("username", "不应恢复的资料")
                    source.getJSONObject("storage").put("crticket-train-query-history", "[\"G7\"]")
                    source.getJSONObject("native").put("vehicleSearchHistory", JSONArray().put("G7"))
                    val before = fixture.storage()
                    val beforeNative = BackupPreferences(context).snapshot().apply { remove("vehicleSearchHistory") }
                    val result = fixture.import(full.toString(), settings = true, excluded = excluded)
                    assertTrue(result.toString(), result.getBoolean("success"))
                    val after = fixture.storage()
                    assertEquals("[\"G7\"]", after.getString("crticket-train-query-history"))
                    for (key in before.keys()) {
                        if (key !in setOf("crticket-train-query-history", "crticket-station-query-history")) {
                            assertEquals(key, before.get(key), after.get(key))
                        }
                    }
                    val afterNative = BackupPreferences(context).snapshot()
                    assertEquals("[\"G7\"]", afterNative.getJSONArray("vehicleSearchHistory").toString())
                    afterNative.remove("vehicleSearchHistory")
                    assertEquals(beforeNative.toString(), afterNative.toString())

                    // A partial file only offers its actual contents; defaults reset on each opening.
                    assertTrue(fixture.import(partial.toString(), settings = true).getBoolean("success"))
                } finally {
                    fixture.restoreStorage(storage)
                }
            }
        } finally {
            history.edit().apply {
                if (originalHistory == null) remove("history") else putString("history", originalHistory)
            }.commit()
            android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
        }
    }
}

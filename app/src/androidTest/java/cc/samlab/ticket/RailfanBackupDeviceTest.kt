package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalHistory
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import cc.samlab.ticket.ui.railfan.VehicleSearchHistory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RailfanBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val keys = mapOf(
        "android" to listOf("railfanToolsEnabled", IMPORTANT_NOTICE_SUPPRESSED),
        "station-arrival" to listOf("firstVisits"),
        "vehicle-query" to listOf("history"),
    )
    private lateinit var original: Map<String, Map<String, Any?>>
    private val time = 1_790_000_000_000L

    @Before fun preserveDataAndWakeDevice() {
        original = keys.mapValues { (name, fields) ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            fields.associateWith { preferences.all[it] }
        }
        context.getSharedPreferences("android", Context.MODE_PRIVATE).edit()
            .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP"),
        ).use { it.readBytes() }
    }

    @After fun restoreOriginalData() {
        original.forEach { (name, fields) ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().apply {
                fields.forEach { (key, value) ->
                    when (value) {
                        null -> remove(key)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }.commit()
        }
    }

    @Test fun settingsFileExportImportAndReimportPreserveRailfanRecords() {
        seedSource()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity {
                activity = it
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
            }
            val fixture = BackupDeviceFixture(compose, activity)
            fixture.ready()
            val storage = fixture.storage()
            try {
                val backup = fixture.export()
                val exported = backup.getJSONObject("settings").getJSONObject("native")
                assertTrue(exported.getBoolean("railfanToolsEnabled"))
                assertEquals(2, exported.getJSONArray("stationVisits").length())
                assertEquals("G1", exported.getJSONArray("vehicleSearchHistory").getString(0))
                // Exercise new portable data without changing the phone's locale or live tasks.
                val native = JSONObject()
                listOf("railfanToolsEnabled", "stationVisits", "vehicleSearchHistory").forEach {
                    native.put(it, exported.get(it))
                }
                backup.put("tickets", JSONArray()).put("settings", JSONObject().put("native", native))
                RailfanPreferences.setEnabled(context, false)
                setVisits(JSONArray().put(visit("备份验证站甲", time + 500))
                    .put(visit("备份验证站丙", time + 1000)))
                VehicleSearchHistory(context).clear()

                repeat(2) {
                    val result = fixture.import(backup.toString(), settings = true)
                    assertTrue(result.toString(), result.getBoolean("success"))
                    verifyImported()
                }
                val roundTrip = fixture.export().getJSONObject("settings").getJSONObject("native")
                assertTrue(roundTrip.getBoolean("railfanToolsEnabled"))
                assertEquals(3, roundTrip.getJSONArray("stationVisits").length())
                assertEquals(native.getJSONArray("vehicleSearchHistory").toString(),
                    roundTrip.getJSONArray("vehicleSearchHistory").toString())
            } finally {
                fixture.restoreStorage(storage)
            }
        }
    }

    @Test fun legacyFieldsAndInvalidRecordsNeverReplaceExistingRailfanData() {
        seedSource()
        val preferences = BackupPreferences(context)
        val before = preferences.snapshot().toString()
        preferences.restore(JSONObject())
        assertEquals(before, preferences.snapshot().toString())
        for (invalid in listOf(
            JSONObject().put("railfanToolsEnabled", "true"),
            JSONObject().put("stationVisits", JSONArray().put(visit("北京南", -1))),
            JSONObject().put("stationVisits", JSONArray().put(visit("北京南", time).put("time", "$time"))),
            JSONObject().put("stationVisits", JSONArray().put(visit("北京南", time).put("historical", 1))),
            JSONObject().put("vehicleSearchHistory", JSONArray().put("../G1")),
        )) {
            assertTrue(runCatching { preferences.restore(invalid) }.isFailure)
            assertEquals(before, preferences.snapshot().toString())
        }
    }

    private fun seedSource() {
        RailfanPreferences.setEnabled(context, true)
        setVisits(JSONArray().put(visit("备份验证站甲", time))
            .put(visit("备份验证站乙", time - 1000, historical = true)))
        VehicleSearchHistory(context).clear()
        VehicleSearchHistory(context).add("CR200JB2201")
        VehicleSearchHistory(context).add("G1")
    }

    private fun verifyImported() {
        assertTrue(RailfanPreferences.enabled(context))
        assertEquals(listOf("G1", "CR200JB2201"), VehicleSearchHistory(context).read())
        val visits = ArrivalHistory(context).visits()
        assertEquals(3, visits.size)
        assertEquals(time, visits.single { it.station == "备份验证站甲" }.firstArrival)
        assertTrue(visits.single { it.station == "备份验证站乙" }.historicalReminder)
        assertEquals(time + 1000, visits.single { it.station == "备份验证站丙" }.firstArrival)
    }

    private fun visit(station: String, time: Long, historical: Boolean = false) = JSONObject()
        .put("station", station).put("time", time).put("historical", historical)

    private fun setVisits(rows: JSONArray) {
        context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE).edit()
            .putString("firstVisits", rows.toString()).commit()
    }
}

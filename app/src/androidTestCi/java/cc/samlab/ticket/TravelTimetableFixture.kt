package cc.samlab.ticket

import android.util.Base64
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class TravelTimetableFixture(private val fixture: TabletPageFixture) {
    private val preferences = fixture.activity.getSharedPreferences("developer-options", 0)
    private val autoUpdate = preferences.getBoolean("lltAutoUpdate", true)
    private val hadAutoUpdate = preferences.contains("lltAutoUpdate")
    private val versions = fixture.activity.getSharedPreferences("data-versions", 0)
    private val oldVersion = versions.getString("llt", null)

    fun install(compose: ComposeTestRule) {
        preferences.edit().putBoolean("lltAutoUpdate", false).commit()
        val archive = archive()
        val base64 = Base64.encodeToString(archive, Base64.NO_WRAP)
        val hash = MessageDigest.getInstance("SHA-256").digest(archive).joinToString("") { "%02x".format(it) }
        fixture.evaluate(
            """
            (() => {
                const openDatabase = indexedDB.open.bind(indexedDB)
                indexedDB.open = (name, version) => openDatabase(
                    name === 'crticket-phone-timetable' ? 'crticket-travel-device-test' : name, version)
                const test = async () => {
                    const timetable = window.crticketTimetable
                    await timetable.configure()
                    timetable.begin('travel-test', ${archive.size})
                    timetable.append('travel-test', '$base64')
                    await timetable.finish('travel-test')
                    await timetable.select('travel-test', 'import:20000102:$hash')
                    window.travelTestVersion = await timetable.snapshot(true)
                }
                test()
            })()
            """.trimIndent(),
        )
        compose.waitUntil(30_000) { fixture.evaluate("window.travelTestVersion") == "\"20000102\"" }
    }

    fun restore() {
        fixture.evaluate("indexedDB.deleteDatabase('crticket-travel-device-test'); null")
        val auto = preferences.edit()
        if (hadAutoUpdate) auto.putBoolean("lltAutoUpdate", autoUpdate) else auto.remove("lltAutoUpdate")
        auto.commit()
        val version = versions.edit()
        if (oldVersion == null) version.remove("llt") else version.putString("llt", oldVersion)
        version.commit()
    }

    private fun archive(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun add(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            add("ver.txt", "20000102".encodeToByteArray())
            add("t.i", dictionary("V23", "V24"))
            add("s.i", dictionary("北京", "上海虹桥", "二连"))
            add("T0.dat", record(0, listOf(0, 1, 2), listOf(0, 210, 420), 20260105) +
                record(1, listOf(2, 0, 1), listOf(0, 310, 500), 20991231))
            for (index in 1..19) add("T$index.dat", byteArrayOf())
        }
        return bytes.toByteArray()
    }

    private fun record(train: Int, stations: List<Int>, distances: List<Int>, endDate: Int): ByteArray {
        val row = ByteArray(17 + 7 * stations.size)
        row[1] = train.toByte()
        row[16] = stations.size.toByte()
        for (digit in 0..3) {
            row[5 + digit] = ((20260101 shr (digit * 7)) and 127).toByte()
            row[9 + digit] = ((endDate shr (digit * 7)) and 127).toByte()
        }
        stations.forEachIndexed { index, station ->
            val start = 17 + index * 7
            row[start + 1] = station.toByte()
            row[start + 2] = (8 + index * 2).toByte()
            row[start + 5] = (distances[index] / 255).toByte()
            row[start + 6] = (distances[index] % 255).toByte()
        }
        return row
    }

    private fun dictionary(vararg names: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(names.size)
            names.forEach { name ->
                val bytes = name.encodeToByteArray()
                data.writeShort(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }
}

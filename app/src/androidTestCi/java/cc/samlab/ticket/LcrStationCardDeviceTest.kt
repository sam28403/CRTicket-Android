package cc.samlab.ticket

import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LcrStationCardDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun vientianeCardAndBoardShowElevenTrains() {
        val fixture = TabletPageFixture(compose)
        val preferences = fixture.activity.getSharedPreferences("developer-options", 0)
        val autoUpdate = preferences.getBoolean("lltAutoUpdate", true)
        try {
            preferences.edit().putBoolean("lltAutoUpdate", false).apply()
            val archive = Base64.encodeToString(archive(), Base64.NO_WRAP)
            fixture.evaluate("""
                Date.now = () => Date.parse('2026-11-12T07:00:00Z');
                const openDatabase = indexedDB.open.bind(indexedDB);
                indexedDB.open = (name, version) => openDatabase(
                    name === 'crticket-phone-timetable' ? 'lcr-station-card-test' : name, version);
                const originalFetch = window.fetch.bind(window);
                window.fetch = (url, options) => {
                    if (url !== '/offline/an.db') return originalFetch(url, options);
                    window.vientianeFixtureLoaded = true;
                    return Promise.resolve(new Response(Uint8Array.from(atob('$archive'), c => c.charCodeAt(0))));
                };
            """.trimIndent())
            if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("important-notice-confirm").performClick()
            }
            fixture.navigate("/search")
            val field = compose.onNodeWithTag("global-search-field")
            field.performTextReplacement("万象")
            field.performImeAction()
            val card = hasTestTag("station-random-card:万象")
            compose.waitUntil(30_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("true", fixture.evaluate("window.vientianeFixtureLoaded"))
            val total = fixture.activity.getString(R.string.train_list_total, 11)
            compose.onNode(card).assertTextContains(total)
            fixture.capture("vientiane-station-card-11")
            compose.onNode(card).performClick()
            val boardTotal = hasText(total) and hasAnyAncestor(hasTestTag("station-controls"))
            compose.waitUntil(30_000) {
                compose.onAllNodes(boardTotal, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(boardTotal, useUnmergedTree = true).assertIsDisplayed()
            fixture.capture("vientiane-station-board-11")
        } finally {
            fixture.evaluate("indexedDB.deleteDatabase('lcr-station-card-test'); null")
            preferences.edit().putBoolean("lltAutoUpdate", autoUpdate).apply()
            fixture.close()
        }
    }

    private fun archive(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun add(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            add("ver.txt", "20261112".encodeToByteArray())
            add("t.i", dictionary("D83", "D84", "D87", "D88"))
            add("s.i", dictionary("万象", "磨憨"))
            val records = ByteArray(4 * 31)
            repeat(4) { index ->
                val offset = index * 31
                records[offset + 1] = index.toByte()
                val date = 20261112
                repeat(4) { digit ->
                    val value = ((date shr (digit * 7)) and 127).toByte()
                    records[offset + 5 + digit] = value
                    records[offset + 9 + digit] = value
                }
                records[offset + 16] = 2
                records[offset + 19] = (10 + index).toByte()
                records[offset + 25] = 1
                records[offset + 26] = (14 + index).toByte()
                records[offset + 30] = 100
            }
            add("T0.dat", records)
            for (index in 1..19) add("T$index.dat", byteArrayOf())
        }
        return output.toByteArray()
    }

    private fun dictionary(vararg names: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(names.size)
            for (name in names) {
                val bytes = name.encodeToByteArray()
                data.writeShort(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }
}

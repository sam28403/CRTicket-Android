package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneratorDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun expressiveFormPreviewsRetainsDraftDeduplicatesExportsAndClearsExplicitly() {
        val fixture = TabletPageFixture(compose)
        val preferences = fixture.activity.getSharedPreferences("ticket-generator", 0)
        val previousDraft = preferences.getString("draft", null)
            ?.takeUnless { it.contains("CODEX_GENERATOR_DEVICE_TEST") }
        fixture.shell("input keyevent KEYCODE_WAKEUP")
        val previousStorage = fixture.evaluate("""
            JSON.stringify(['crticket-generator-draft', 'crticket-generator-receipt'].map(key =>
                [key, localStorage.getItem(key)?.includes('CODEX_GENERATOR_DEVICE_TEST')
                    ? null : localStorage.getItem(key)]))
        """.trimIndent())
        try {
            fixture.navigate("/")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("generator-editor").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.evaluate("""
                CRTicketAndroid.generatorResult(JSON.stringify({reset: true, draft: {
                    number: 'CODEX_GENERATOR_DEVICE_TEST', trainNo: 'G25', from: '北京南', to: '上海虹桥',
                    date: '2026年10月18日', time: '08:00', price: '553', seatType: '二等座',
                    seatNo: '03车12A号', distance: '1318', theme: 'EMU_Green.jpg',
                    message: '中国铁路祝您旅途愉快', sellPlace: '北京南站', gate: '1A',
                    useCredit: 0, hasConditioner: 0
                }}));
            """.trimIndent())
            compose.waitUntil(10_000) {
                fixture.evaluate("JSON.parse(CRTicketAndroid.generatorDraft()).number") ==
                    "\"CODEX_GENERATOR_DEVICE_TEST\""
            }
            compose.onNodeWithTag("history-field-trainNo").performScrollTo().performTextReplacement("G103")
            closeSoftKeyboard()
            compose.waitUntil(10_000) {
                fixture.evaluate("JSON.parse(CRTicketAndroid.generatorDraft()).trainNo") == "\"G103\""
            }
            fixture.capture("generator-form")
            compose.onNodeWithTag("generator-image").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("generator-pdf").assertIsDisplayed()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("generator-preview").fetchSemanticsNodes().isNotEmpty()
            }
            val previewWidth = compose.onNodeWithTag("generator-preview").fetchSemanticsNode().boundsInRoot.width
            assertTrue(
                "Tablet preview must stay compact instead of filling the detail pane",
                previewWidth <= 440 * fixture.activity.resources.displayMetrics.density + 2,
            )
            fixture.capture("generator-preview-actions")
            compose.onNodeWithTag("generator-pdf").performClick()
            waitForPicker(fixture)
            assertEquals("1", testRecordCount(fixture))
            cancelPicker(fixture)
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("generator-image").assertIsEnabled(); true }
                    .getOrDefault(false)
            }
            fixture.navigate("/home")
            fixture.navigate("/")
            compose.onNodeWithTag("history-field-trainNo").assertTextContains("G103")
            compose.onNodeWithTag("history-field-number").performScrollTo()
                .assertTextContains("CODEX_GENERATOR_DEVICE_TEST")
            compose.onNodeWithTag("generator-image").performScrollTo().performClick()
            waitForPicker(fixture)
            assertEquals("A second format must not create another account record", "1", testRecordCount(fixture))
            cancelPicker(fixture)
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("generator-image").assertIsEnabled(); true }
                    .getOrDefault(false)
            }
            val savedDraft = fixture.evaluate("CRTicketAndroid.generatorDraft()")
            for (format in listOf("pdf", "image")) {
                val tag = if (format == "pdf") "generator-pdf" else "generator-image"
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                waitForPicker(fixture)
                completeGeneratorExport(fixture, format)
                cancelPicker(fixture)
                compose.waitUntil(10_000) {
                    fixture.evaluate("JSON.parse(CRTicketAndroid.generatorDraft()).number") == "\"\""
                }
                assertEquals("1", testRecordCount(fixture))
                fixture.evaluate("""
                    CRTicketAndroid.generatorResult(JSON.stringify({
                        reset: true, draft: JSON.parse($savedDraft)
                    }));
                """.trimIndent())
                compose.waitUntil(10_000) {
                    fixture.evaluate("JSON.parse(CRTicketAndroid.generatorDraft()).number") ==
                        "\"CODEX_GENERATOR_DEVICE_TEST\""
                }
            }
            // Invalid input returns a native error and never opens the file picker.
            compose.onNodeWithTag("history-field-trainNo").performScrollTo().performTextReplacement("invalid")
            closeSoftKeyboard()
            compose.onNodeWithTag("generator-pdf").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("请输入正确的车次").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("generator-clear").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                fixture.evaluate("JSON.parse(CRTicketAndroid.generatorDraft()).number") == "\"\""
            }
            fixture.navigate("/home")
            fixture.navigate("/")
            compose.onNodeWithTag("history-field-number").performScrollTo()
                .assertTextEquals("票号 / 订单号", "")
        } finally {
            runCatching { cancelPicker(fixture) }
            runCatching { closeSoftKeyboard() }
            fixture.evaluate("""
                localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(
                    JSON.parse(localStorage.getItem('crticket-device-tickets-v1') || '[]')
                        .filter(row => row.ticket_number !== 'CODEX_GENERATOR_DEVICE_TEST')
                ));
                JSON.parse($previousStorage).forEach(([key, value]) => {
                    if (value === null) localStorage.removeItem(key);
                    else localStorage.setItem(key, value);
                });
            """.trimIndent())
            fixture.close()
            compose.waitForIdle()
            if (previousDraft == null) preferences.edit().remove("draft").commit()
            else preferences.edit().putString("draft", previousDraft).commit()
        }
    }

    private fun waitForPicker(fixture: TabletPageFixture) {
        compose.waitUntil(30_000) {
            fixture.shell("dumpsys activity activities").lineSequence().any {
                it.contains("Resumed") && it.contains("documentsui")
            }
        }
    }

    private fun cancelPicker(fixture: TabletPageFixture) {
        // Cancel the actual activity result, even when the device keyguard covers
        // DocumentsUI. Do not send Back to the user's lock screen.
        fixture.instrumentation.runOnMainSync { fixture.activity.finishActivity(11) }
    }

    private fun testRecordCount(fixture: TabletPageFixture): String = fixture.evaluate("""
        JSON.parse(localStorage.getItem('crticket-device-tickets-v1') || '[]')
            .filter(row => row.ticket_number === 'CODEX_GENERATOR_DEVICE_TEST').length
    """.trimIndent())
}
